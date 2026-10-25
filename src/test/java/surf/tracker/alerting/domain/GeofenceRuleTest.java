package com.surf.tracker.alerting.domain;

import com.surf.tracker.alerting.domain.enums.AlertSeverity;
import com.surf.tracker.shared.domain.LocationPoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class GeofenceRuleTest {

    @Test
    void evaluate_entryAction_insideGeofence_returnsEntryAlert() {
        // given
        List<LocationPoint> boundary = List.of(
            new LocationPoint(40.7120, -74.0070, 0.0f, Instant.now()),
            new LocationPoint(40.7120, -74.0050, 0.0f, Instant.now()),
            new LocationPoint(40.7110, -74.0050, 0.0f, Instant.now()),
            new LocationPoint(40.7110, -74.0070, 0.0f, Instant.now())
        );

        GeofenceRule rule = new GeofenceRule(
            "geofence-rule",
            "Geofence Entry Alert",
            "geofence-1",
            boundary,
            GeofenceRule.Action.ENTRY,
            2
        );

        // Simulate being outside first
        rule.evaluate("vehicle-001",
            new LocationPoint(40.7130, -74.0080, 10.0f, Instant.now()));

        // Now inside
        LocationPoint insideLocation =
            new LocationPoint(40.7115, -74.0060, 10.0f, Instant.now());

        // when
        var result = rule.evaluate("vehicle-001", insideLocation);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getMessage()).contains("entered");
        assertThat(result.getSeverity()).isEqualTo(AlertSeverity.INFO);
    }

    @Test
    void evaluate_exitAction_outsideGeofence_returnsExitAlert() {
        // given
        List<LocationPoint> boundary = List.of(
            new LocationPoint(40.7120, -74.0070, 0.0f, Instant.now()),
            new LocationPoint(40.7120, -74.0050, 0.0f, Instant.now()),
            new LocationPoint(40.7110, -74.0050, 0.0f, Instant.now()),
            new LocationPoint(40.7110, -74.0070, 0.0f, Instant.now())
        );

        GeofenceRule rule = new GeofenceRule(
            "geofence-rule",
            "Geofence Exit Alert",
            "geofence-1",
            boundary,
            GeofenceRule.Action.EXIT,
            2
        );

        // Simulate being inside first
        rule.evaluate("vehicle-001",
            new LocationPoint(40.7115, -74.0060, 10.0f, Instant.now()));

        // Now outside
        LocationPoint outsideLocation =
            new LocationPoint(40.7130, -74.0080, 10.0f, Instant.now());

        // when
        var result = rule.evaluate("vehicle-001", outsideLocation);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getMessage()).contains("exited");
        assertThat(result.getSeverity()).isEqualTo(AlertSeverity.WARNING);
    }

    @Test
    void evaluate_bothAction_transition_returnsAlerts() {
        // given
        List<LocationPoint> boundary = List.of(
            new LocationPoint(40.7120, -74.0070, 0.0f, Instant.now()),
            new LocationPoint(40.7120, -74.0050, 0.0f, Instant.now()),
            new LocationPoint(40.7110, -74.0050, 0.0f, Instant.now()),
            new LocationPoint(40.7110, -74.0070, 0.0f, Instant.now())
        );

        GeofenceRule rule = new GeofenceRule(
            "geofence-rule",
            "Geofence Alert",
            "geofence-1",
            boundary,
            GeofenceRule.Action.BOTH,
            2
        );

        // Start outside
        var result1 = rule.evaluate("vehicle-001",
            new LocationPoint(40.7130, -74.0080, 10.0f, Instant.now()));
        assertThat(result1).isNull();

        // Enter geofence
        var result2 = rule.evaluate("vehicle-001",
            new LocationPoint(40.7115, -74.0060, 10.0f, Instant.now()));
        assertThat(result2).isNotNull();
        assertThat(result2.getMessage()).contains("entered");

        // Exit geofence
        var result3 = rule.evaluate("vehicle-001",
            new LocationPoint(40.7130, -74.0080, 10.0f, Instant.now()));
        assertThat(result3).isNotNull();
        assertThat(result3.getMessage()).contains("exited");
    }

    @Test
    void evaluate_disabledRule_returnsNull() {
        // given
        List<LocationPoint> boundary = List.of(
            new LocationPoint(40.7120, -74.0070, 0.0f, Instant.now()),
            new LocationPoint(40.7120, -74.0050, 0.0f, Instant.now()),
            new LocationPoint(40.7110, -74.0050, 0.0f, Instant.now()),
            new LocationPoint(40.7110, -74.0070, 0.0f, Instant.now())
        );

        GeofenceRule rule = new GeofenceRule(
            "geofence-rule",
            "Geofence Alert",
            "geofence-1",
            boundary,
            GeofenceRule.Action.BOTH,
            2,
            false
        );

        // when
        var result = rule.evaluate("vehicle-001",
            new LocationPoint(40.7115, -74.0060, 10.0f, Instant.now()));

        // then
        assertThat(result).isNull();
    }

    @Test
    void evaluate_insufficientBoundaryPoints_returnsNull() {
        // given
        List<LocationPoint> boundary = List.of(
            new LocationPoint(40.7120, -74.0070, 0.0f, Instant.now()),
            new LocationPoint(40.7120, -74.0050, 0.0f, Instant.now())
        ); // Only 2 points, need at least 3 for polygon

        GeofenceRule rule = new GeofenceRule(
            "geofence-rule",
            "Geofence Alert",
            "geofence-1",
            boundary,
            GeofenceRule.Action.BOTH,
            2
        );

        // when
        var result = rule.evaluate("vehicle-001",
            new LocationPoint(40.7115, -74.0060, 10.0f, Instant.now()));

        // then
        assertThat(result).isNull();
    }

    @Test
    void getPriority_returnsConfiguredPriority() {
        // given
        List<LocationPoint> boundary = List.of(
            new LocationPoint(40.7120, -74.0070, 0.0f, Instant.now()),
            new LocationPoint(40.7120, -74.0050, 0.0f, Instant.now()),
            new LocationPoint(40.7110, -74.0050, 0.0f, Instant.now())
        );

        GeofenceRule rule = new GeofenceRule(
            "geofence-rule",
            "Geofence Alert",
            "geofence-1",
            boundary,
            GeofenceRule.Action.BOTH,
            5
        );

        // when & then
        assertThat(rule.getPriority()).isEqualTo(5);
    }
}
