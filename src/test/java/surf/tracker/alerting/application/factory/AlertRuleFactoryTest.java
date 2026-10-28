package com.surf.tracker.alerting.application.factory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.surf.tracker.alerting.domain.*;
import com.surf.tracker.alerting.domain.entity.AlertRule;
import com.surf.tracker.alerting.domain.entity.Geofence;
import com.surf.tracker.alerting.domain.enums.AlertRuleType;
import com.surf.tracker.alerting.domain.parameters.RuleParametersMapper;
import com.surf.tracker.alerting.application.service.GeofenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertRuleFactoryTest {

    @Mock
    private GeofenceService geofenceService;

    @Mock
    private RuleStateStore stateStore;

    private AlertRuleFactory alertRuleFactory;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-30T12:00:00Z"), ZoneOffset.UTC);
        alertRuleFactory = new AlertRuleFactory(geofenceService, stateStore, clock,
                new RuleParametersMapper(new ObjectMapper()));
    }

    @Test
    void createDomainRule_speedRule_returnsMaxSpeedRule() {
        // given
        AlertRule entity = AlertRuleTestBuilder.defaultRule()
            .ruleType(AlertRuleType.SPEED)
            .parameter("speedLimit", 80.0f)
            .vehicleId("vehicle-001")
            .enabled(true)
            .build();

        // when
        IAlertRule result = alertRuleFactory.createDomainRule(entity, "vehicle-001");

        // then
        assertThat(result).isInstanceOf(MaxSpeedRule.class);
        assertThat(result.getRuleKey()).isEqualTo("test-rule-key");
        assertThat(result.isEnabled()).isTrue();
    }

    @Test
    void createDomainRule_timeRule_returnsIdleTimeRule() {
        // given
        AlertRule entity = AlertRuleTestBuilder.defaultRule()
            .ruleType(AlertRuleType.TIME)
            .parameter("maxIdleMinutes", 30)
            .vehicleId("vehicle-001")
            .build();

        // when
        IAlertRule result = alertRuleFactory.createDomainRule(entity, "vehicle-001");

        // then
        assertThat(result).isInstanceOf(IdleTimeRule.class);
    }

    @Test
    void createDomainRule_geofenceRule_returnsGeofenceRule() {
        // given
        AlertRule entity = AlertRuleTestBuilder.defaultRule()
            .ruleType(AlertRuleType.GEOFENCE)
            .parameter("geofenceId", "1")
            .parameter("action", "BOTH")
            .vehicleId("vehicle-001")
            .build();

        Geofence geofence = GeofenceTestBuilder.circularGeofence()
            .geofenceId(1L)
            .vehicleId("vehicle-001")
            .build();

        when(geofenceService.getGeofenceById(1L)).thenReturn(geofence);

        // when
        IAlertRule result = alertRuleFactory.createDomainRule(entity, "vehicle-001");

        // then
        assertThat(result).isInstanceOf(GeofenceRule.class);
        GeofenceRule geofenceRule = (GeofenceRule) result;
        assertThat(geofenceRule.getGeofenceId()).isEqualTo("1");
    }

    @Test
    void createDomainRule_disabledRule_returnsNull() {
        // given
        AlertRule entity = AlertRuleTestBuilder.defaultRule()
            .enabled(false)
            .vehicleId("vehicle-001")
            .build();

        // when
        IAlertRule result = alertRuleFactory.createDomainRule(entity, "vehicle-001");

        // then
        assertThat(result).isNull();
    }

    @Test
    void createDomainRule_wrongVehicle_returnsNull() {
        // given
        AlertRule entity = AlertRuleTestBuilder.defaultRule()
            .vehicleId("vehicle-001")
            .build();

        // when
        IAlertRule result = alertRuleFactory.createDomainRule(entity, "vehicle-002");

        // then
        assertThat(result).isNull();
    }

    @Test
    void createDomainRule_unknownRuleType_returnsGenericRule() {
        // given
        AlertRule entity = AlertRuleTestBuilder.defaultRule()
            .ruleType(AlertRuleType.CUSTOM)
            .vehicleId("vehicle-001")
            .build();

        // when
        IAlertRule result = alertRuleFactory.createDomainRule(entity, "vehicle-001");

        // then
        assertThat(result).isInstanceOf(GenericAlertRule.class);
    }

    @Test
    void createDomainRule_missingParameters_returnsRuleWithDefaults() {
        // given
        AlertRule entity = AlertRuleTestBuilder.defaultRule()
            .ruleType(AlertRuleType.SPEED)
            .vehicleId("vehicle-001")
            .build();

        // when
        IAlertRule result = alertRuleFactory.createDomainRule(entity, "vehicle-001");

        // then
        assertThat(result).isNotNull();
    }

    @Test
    void createDomainRule_geofenceNotFound_returnsNull() {
        // given
        AlertRule entity = AlertRuleTestBuilder.defaultRule()
            .ruleType(AlertRuleType.GEOFENCE)
            .parameter("geofenceId", "999")
            .vehicleId("vehicle-001")
            .build();

        when(geofenceService.getGeofenceById(999L)).thenReturn(null);

        // when
        IAlertRule result = alertRuleFactory.createDomainRule(entity, "vehicle-001");

        // then
        assertThat(result).isNull();
    }
}
