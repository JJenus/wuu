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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlertingEngineTest {
    private static final AlertDeduplicator.Lease LEASE = new AlertDeduplicator.Lease("test-key", "test-attempt");

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

    @AfterEach
    void clearTenant() { TenantContext.clear(); }

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
        when(deduplicator.acquire(any(AlertDetectedEvent.class))).thenReturn(LEASE);

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
        when(deduplicator.acquire(any(AlertDetectedEvent.class))).thenReturn(LEASE);

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
    void processVehicleUpdate_publicationFailure_releasesCooldownAndPropagatesForRedelivery() {
        // given
        String vehicle = "retry-vehicle";
        LocationPoint location = new LocationPoint(1, 2, 60, Instant.parse("2026-01-01T12:00:00Z"));
        AlertRule rule = AlertRuleTestBuilder.geofenceRule().vehicleId(vehicle).build();
        IAlertRule domain = mock(com.surf.tracker.alerting.domain.GeofenceRule.class);
        AlertDetectedEvent detected = new AlertDetectedEvent("retry-rule", AlertType.GEOFENCE_ENTRY, vehicle,
            "Geofence entered", com.surf.tracker.alerting.domain.enums.AlertSeverity.WARNING, location);
        when(vehicleRuleCacheService.hasRulesCached(vehicle)).thenReturn(true);
        when(vehicleRuleCacheService.hasActiveRules(vehicle, 1L)).thenReturn(true);
        when(vehicleRuleCacheService.getActiveRulesForVehicle(vehicle, 1L)).thenReturn(List.of(rule));
        when(ruleFactory.createDomainRule(rule, vehicle)).thenReturn(domain);
        when(evaluationService.evaluateRule(domain, vehicle, location)).thenReturn(detected);
        when(deduplicator.acquire(detected)).thenReturn(LEASE);
        RuntimeException failure = new RuntimeException("broker unavailable");
        doThrow(failure).when(eventPublisher).publish(detected);
        // when / then
        assertThatThrownBy(() -> alertingEngine.processVehicleUpdate(vehicle, location)).isSameAs(failure);
        verify(deduplicator).release(LEASE);
        verifyNoInteractions(stateStore);
    }

    @Test
    void processVehicleUpdate_sameSourceRedelivery_preservesDerivedAlertIdentityAndTime() {
        // given: domain evaluation creates a new object/clock value on every attempt.
        String vehicle = "stable-vehicle";
        var timestamp = Instant.parse("2026-01-01T12:00:00Z");
        var location = new LocationPoint(1, 2, 60, timestamp);
        var source = new com.surf.tracker.shared.events.VehicleUpdatedEvent("source-one", timestamp,
            vehicle, 1L, location, java.util.Map.of());
        AlertRule rule = AlertRuleTestBuilder.overspeedRule().vehicleId(vehicle).build();
        IAlertRule domain = mock(IAlertRule.class);
        when(vehicleRuleCacheService.hasRulesCached(vehicle)).thenReturn(true);
        when(vehicleRuleCacheService.hasActiveRules(vehicle, 1L)).thenReturn(true);
        when(vehicleRuleCacheService.getActiveRulesForVehicle(vehicle, 1L)).thenReturn(List.of(rule));
        when(ruleFactory.createDomainRule(rule, vehicle)).thenReturn(domain);
        when(evaluationService.evaluateRule(domain, vehicle, location)).thenAnswer(invocation ->
            new AlertDetectedEvent("stable-rule", AlertType.OVERSPEED, vehicle, "Speed exceeded",
                com.surf.tracker.alerting.domain.enums.AlertSeverity.WARNING, location));
        when(deduplicator.acquire(any())).thenReturn(LEASE);
        doThrow(new RuntimeException("ambiguous broker send")).doNothing().when(eventPublisher).publish(any());
        // when: transport may have accepted the first send before reporting failure.
        assertThatThrownBy(() -> alertingEngine.processVehicleUpdate(source)).hasMessage("ambiguous broker send");
        alertingEngine.processVehicleUpdate(source);
        var next = new com.surf.tracker.shared.events.VehicleUpdatedEvent("source-two", timestamp,
            vehicle, 1L, location, java.util.Map.of());
        alertingEngine.processVehicleUpdate(next);
        // then: downstream durable source dedup can recognize the first two attempts, not a new source.
        var emitted = org.mockito.ArgumentCaptor.forClass(AlertDetectedEvent.class);
        verify(eventPublisher, times(3)).publish(emitted.capture());
        var events = emitted.getAllValues();
        assertThat(events.get(1)).usingRecursiveComparison().isEqualTo(events.getFirst());
        assertThat(events.getFirst().getOccurredOn()).isEqualTo(timestamp);
        assertThat(events.get(2).getEventId()).isNotEqualTo(events.getFirst().getEventId());
    }

    @Test
    void processVehicleUpdate_derivedIdentity_separatesTenantAndRuleComponents() {
        // given
        String vehicle = "identity-vehicle";
        var timestamp = Instant.parse("2026-01-01T12:00:00Z");
        var location = new LocationPoint(1, 2, 60, timestamp);
        var firstRule = AlertRuleTestBuilder.overspeedRule().ruleKey("identity-first").vehicleId(vehicle).build();
        var secondRule = AlertRuleTestBuilder.overspeedRule().ruleKey("identity-second").vehicleId(vehicle).build();
        when(vehicleRuleCacheService.hasRulesCached(vehicle)).thenReturn(true);
        when(vehicleRuleCacheService.hasActiveRules(eq(vehicle), anyLong())).thenReturn(true);
        when(vehicleRuleCacheService.getActiveRulesForVehicle(eq(vehicle), anyLong()))
            .thenReturn(List.of(firstRule), List.of(firstRule), List.of(secondRule));
        when(ruleFactory.createDomainRule(any(), eq(vehicle))).thenAnswer(invocation ->
            new com.surf.tracker.alerting.domain.MaxSpeedRule(((AlertRule) invocation.getArgument(0)).getRuleKey(), 30));
        when(evaluationService.evaluateRule(any(), eq(vehicle), eq(location))).thenAnswer(invocation ->
            ((IAlertRule) invocation.getArgument(0)).evaluate(vehicle, location));
        when(deduplicator.acquire(any())).thenReturn(LEASE);
        // when: vary organization independently, then rule independently, with identical source identity.
        for (long organization : new long[]{1L, 2L, 1L}) {
            TenantContext.setCurrentOrgId(organization);
            alertingEngine.processVehicleUpdate(new com.surf.tracker.shared.events.VehicleUpdatedEvent(
                "same-source", timestamp, vehicle, organization, location, java.util.Map.of()));
        }
        // then
        var emitted = org.mockito.ArgumentCaptor.forClass(AlertDetectedEvent.class);
        verify(eventPublisher, times(3)).publish(emitted.capture());
        assertThat(emitted.getAllValues()).extracting(AlertDetectedEvent::getEventId).doesNotHaveDuplicates();
    }

    @Test
    void processVehicleUpdate_foreignSourceOrganization_rejectedBeforeRuleAccess() {
        // given
        var source = new com.surf.tracker.shared.events.VehicleUpdatedEvent("foreign-source", Instant.EPOCH,
            "foreign-vehicle", 2L, new LocationPoint(1, 2, 60, Instant.EPOCH), java.util.Map.of());
        // when / then
        assertThatThrownBy(() -> alertingEngine.processVehicleUpdate(source)).isInstanceOf(ValidationException.class);
        verifyNoInteractions(vehicleRuleCacheService, eventPublisher, evaluationService, deduplicator);
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
