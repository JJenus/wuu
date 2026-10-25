package com.surf.tracker.alerting.application;

import com.surf.tracker.alerting.application.dedup.AlertDeduplicator;
import com.surf.tracker.alerting.application.service.AlertRuleEvaluationService;
import com.surf.tracker.alerting.domain.AlertDetectedEvent;
import com.surf.tracker.alerting.domain.AlertRuleTestBuilder;
import com.surf.tracker.alerting.domain.IAlertRule;
import com.surf.tracker.alerting.domain.RuleStateStore;
import com.surf.tracker.alerting.domain.entity.AlertRule;
import com.surf.tracker.shared.metrics.MetricsRegistry;
import com.surf.tracker.alerting.domain.enums.AlertType;
import com.surf.tracker.alerting.application.factory.AlertRuleFactory;
import com.surf.tracker.alerting.infrastructure.cache.VehicleRuleCacheService;
import com.surf.tracker.shared.domain.LocationPoint;
import com.surf.tracker.shared.exception.ValidationException;
import com.surf.tracker.shared.pubsub.EventPublisher;
import com.surf.tracker.shared.security.TenantContext;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlertingEngineTest {

    @Mock
    private VehicleRuleCacheService vehicleRuleCacheService;

    @Mock
    private EventPublisher eventPublisher;

    @Mock
    private AlertRuleEvaluationService evaluationService;

    @Mock
    private AlertRuleFactory ruleFactory;

    @Mock
    private RuleStateStore stateStore;

    @Mock
    private AlertDeduplicator deduplicator;

    private MetricsRegistry metrics;

    private AlertingEngine alertingEngine;

    @BeforeEach
    void setUp() {
        metrics = new MetricsRegistry(new SimpleMeterRegistry());
        TenantContext.setCurrentOrgId(1L);
        alertingEngine = new AlertingEngine(eventPublisher, evaluationService, vehicleRuleCacheService,
                ruleFactory, stateStore, deduplicator, metrics);
    }

    @Test
    void processVehicleUpdate_nullVehicleId_throwsException() {
        // given
        LocationPoint location = new LocationPoint(40.7128, -74.0060, 60.0f, Instant.now());

        // when & then
        assertThatThrownBy(() -> alertingEngine.processVehicleUpdate(null, location))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("Vehicle and location cannot be null");
    }

    @Test
    void processVehicleUpdate_nullLocation_throwsException() {
        // when & then
        assertThatThrownBy(() -> alertingEngine.processVehicleUpdate("vehicle-001", null))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("Vehicle and location cannot be null");
    }

    @Test
    void processVehicleUpdate_noCachedRules_skipsProcessing() {
        // given
        String vehicleId = "vehicle-001";
        LocationPoint location = new LocationPoint(40.7128, -74.0060, 60.0f, Instant.now());

        when(vehicleRuleCacheService.hasRulesCached(vehicleId)).thenReturn(false);

        // when
        alertingEngine.processVehicleUpdate(vehicleId, location);

        // then
        verify(vehicleRuleCacheService).hasRulesCached(vehicleId);
        verify(vehicleRuleCacheService, never()).getActiveRulesForVehicle(vehicleId, 1L);
        verify(evaluationService, never()).evaluateRule(any(), any(), any());
    }

    @Test
    void processVehicleUpdate_noActiveRules_skipsProcessing() {
        // given
        String vehicleId = "vehicle-001";
        LocationPoint location = new LocationPoint(40.7128, -74.0060, 60.0f, Instant.now());

        when(vehicleRuleCacheService.hasRulesCached(vehicleId)).thenReturn(true);
        when(vehicleRuleCacheService.hasActiveRules(vehicleId, 1L)).thenReturn(false);

        // when
        alertingEngine.processVehicleUpdate(vehicleId, location);

        // then
        verify(vehicleRuleCacheService).hasActiveRules(vehicleId, 1L);
        verify(vehicleRuleCacheService, never()).getActiveRulesForVehicle(vehicleId, 1L);
    }

    @Test
    void processVehicleUpdate_activeRules_processed() {
        // given
        String vehicleId = "vehicle-001";
        LocationPoint location = new LocationPoint(40.7128, -74.0060, 60.0f, Instant.now());

        AlertRule rule1 = AlertRuleTestBuilder.overspeedRule()
            .ruleKey("speed-rule")
            .vehicleId(vehicleId)
            .build();

        AlertRule rule2 = AlertRuleTestBuilder.idleTimeoutRule()
            .ruleKey("idle-rule")
            .vehicleId(vehicleId)
            .build();

        AlertDetectedEvent alert = new AlertDetectedEvent(
            "speed-rule",
            AlertType.OVERSPEED,
            vehicleId,
            "Speed exceeded",
            com.surf.tracker.alerting.domain.enums.AlertSeverity.WARNING,
            location
        );

        when(vehicleRuleCacheService.hasRulesCached(vehicleId)).thenReturn(true);
        when(vehicleRuleCacheService.hasActiveRules(vehicleId, 1L)).thenReturn(true);
        when(vehicleRuleCacheService.getActiveRulesForVehicle(vehicleId, 1L))
            .thenReturn(List.of(rule1, rule2));
        when(ruleFactory.createDomainRule(any(AlertRule.class), eq(vehicleId)))
            .thenAnswer(inv -> new IAlertRule() {
                @Override
                public AlertDetectedEvent evaluate(String v, LocationPoint l) { return alert; }
                @Override public String getRuleKey() { return "stub"; }
                @Override public String getRuleName() { return "stub"; }
                @Override public boolean isEnabled() { return true; }
                @Override public int getPriority() { return 5; }
            });
        when(evaluationService.evaluateRule(any(IAlertRule.class), eq(vehicleId), eq(location)))
            .thenReturn(alert)
            .thenReturn(null);
        when(deduplicator.tryAcquire(any(AlertDetectedEvent.class))).thenReturn(true);

        // when
        alertingEngine.processVehicleUpdate(vehicleId, location);

        // then
        verify(vehicleRuleCacheService).getActiveRulesForVehicle(vehicleId, 1L);
        verify(evaluationService, times(2)).evaluateRule(any(), eq(vehicleId), eq(location));
        verify(eventPublisher, times(1)).publish(alert);
    }

    @Test
    void processVehicleUpdate_ruleEvaluationError_continuesProcessing() {
        // given
        String vehicleId = "vehicle-001";
        LocationPoint location = new LocationPoint(40.7128, -74.0060, 60.0f, Instant.now());

        AlertRule rule1 = AlertRuleTestBuilder.overspeedRule()
            .ruleKey("bad-rule")
            .vehicleId(vehicleId)
            .build();

        AlertRule rule2 = AlertRuleTestBuilder.idleTimeoutRule()
            .ruleKey("good-rule")
            .vehicleId(vehicleId)
            .build();

        AlertDetectedEvent alert = new AlertDetectedEvent(
            "good-rule",
            AlertType.IDLE_TIMEOUT,
            vehicleId,
            "Alert triggered",
            com.surf.tracker.alerting.domain.enums.AlertSeverity.INFO,
            location
        );

        when(vehicleRuleCacheService.hasRulesCached(vehicleId)).thenReturn(true);
        when(vehicleRuleCacheService.hasActiveRules(vehicleId, 1L)).thenReturn(true);
        when(vehicleRuleCacheService.getActiveRulesForVehicle(vehicleId, 1L))
            .thenReturn(List.of(rule1, rule2));
        when(ruleFactory.createDomainRule(any(AlertRule.class), eq(vehicleId)))
            .thenAnswer(inv -> new IAlertRule() {
                @Override
                public AlertDetectedEvent evaluate(String v, LocationPoint l) { return alert; }
                @Override public String getRuleKey() { return "stub"; }
                @Override public String getRuleName() { return "stub"; }
                @Override public boolean isEnabled() { return true; }
                @Override public int getPriority() { return 5; }
            });
        when(evaluationService.evaluateRule(any(IAlertRule.class), eq(vehicleId), eq(location)))
            .thenThrow(new RuntimeException("Evaluation error"))
            .thenReturn(alert);
        when(deduplicator.tryAcquire(any(AlertDetectedEvent.class))).thenReturn(true);

        // when
        alertingEngine.processVehicleUpdate(vehicleId, location);

        // then
        verify(evaluationService, times(2)).evaluateRule(any(), eq(vehicleId), eq(location));
        verify(eventPublisher).publish(alert);
    }

    @Test
    void invalidateVehicleCache_callsCacheService() {
        // given
        String vehicleId = "vehicle-001";

        // when
        alertingEngine.invalidateVehicleCache(vehicleId);

        // then
        verify(vehicleRuleCacheService).invalidateVehicleRules(vehicleId);
    }

    @Test
    void invalidateAllCache_callsCacheService() {
        // when
        alertingEngine.invalidateAllCache();

        // then
        verify(vehicleRuleCacheService).invalidateAllVehicleRules();
    }

    @Test
    void refreshVehicleRules_callsCacheService() {
        // given
        String vehicleId = "vehicle-001";
        when(vehicleRuleCacheService.getActiveRulesForVehicle(vehicleId, 1L))
            .thenReturn(List.of());

        // when
        alertingEngine.refreshVehicleRules(vehicleId);

        // then
        verify(vehicleRuleCacheService).invalidateVehicleRules(vehicleId);
        verify(vehicleRuleCacheService).getActiveRulesForVehicle(vehicleId, 1L);
    }
}
