package com.surf.tracker.alerting.domain;

import com.surf.tracker.shared.domain.LocationPoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class MaxSpeedRuleTest {

    @Test
    void evaluate_speedBelowThreshold_returnsNull() {
        // given
        MaxSpeedRule rule = new MaxSpeedRule("speed-rule", 80.0f);
        LocationPoint location = LocationPointTestBuilder.defaultLocation()
            .speedKmh(60.0f)
            .build();

        // when
        var result = rule.evaluate("vehicle-001", location);

        // then
        assertThat(result).isNull();
    }

    @Test
    void evaluate_speedAboveThreshold_returnsAlert() {
        // given
        MaxSpeedRule rule = new MaxSpeedRule("speed-rule", 80.0f);
        LocationPoint location = LocationPointTestBuilder.defaultLocation()
            .speedKmh(100.0f)
            .build();

        // when
        var result = rule.evaluate("vehicle-001", location);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getRuleKey()).isEqualTo("speed-rule");
        assertThat(result.getVehicleId()).isEqualTo("vehicle-001");
        assertThat(result.getMessage()).contains("exceeded speed limit");
    }

    @Test
    void evaluate_speedExactlyAtThreshold_returnsNull() {
        // given
        MaxSpeedRule rule = new MaxSpeedRule("speed-rule", 80.0f);
        LocationPoint location = LocationPointTestBuilder.defaultLocation()
            .speedKmh(80.0f)
            .build();

        // when
        var result = rule.evaluate("vehicle-001", location);

        // then
        assertThat(result).isNull();
    }

    @Test
    void evaluate_speedFarAboveThreshold_returnsCriticalAlert() {
        // given
        MaxSpeedRule rule = new MaxSpeedRule("speed-rule", 80.0f);
        LocationPoint location = LocationPointTestBuilder.defaultLocation()
            .speedKmh(121.0f) // exceeds 1.5x threshold
            .build();

        // when
        var result = rule.evaluate("vehicle-001", location);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getSeverity().name()).isEqualTo("CRITICAL");
    }

    @Test
    void evaluate_disabledRule_returnsNull() {
        // given
        MaxSpeedRule rule = new MaxSpeedRule("speed-rule", "MAX_SPEED_RULE", 80.0f, false, 2);
        LocationPoint location = LocationPointTestBuilder.overspeedLocation()
            .build();

        // when
        var result = rule.evaluate("vehicle-001", location);

        // then
        assertThat(result).isNull();
    }

    @Test
    void evaluate_nullVehicleId_returnsNull() {
        // given
        MaxSpeedRule rule = new MaxSpeedRule("speed-rule", 80.0f);
        LocationPoint location = LocationPointTestBuilder.overspeedLocation()
            .build();

        // when
        var result = rule.evaluate(null, location);

        // then
        assertThat(result).isNull();
    }

    @Test
    void evaluate_nullLocation_returnsNull() {
        // given
        MaxSpeedRule rule = new MaxSpeedRule("speed-rule", 80.0f);

        // when
        var result = rule.evaluate("vehicle-001", null);

        // then
        assertThat(result).isNull();
    }

    @Test
    void getRuleKey_returnsConfiguredKey() {
        // given
        MaxSpeedRule rule = new MaxSpeedRule("speed-rule", 80.0f);

        // when & then
        assertThat(rule.getRuleKey()).isEqualTo("speed-rule");
    }

    @Test
    void getRuleName_returnsDefaultName() {
        // given
        MaxSpeedRule rule = new MaxSpeedRule("speed-rule", 80.0f);

        // when & then
        assertThat(rule.getRuleName()).isEqualTo("MAX_SPEED_RULE");
    }

    @Test
    void getPriority_returnsDefaultPriority() {
        // given
        MaxSpeedRule rule = new MaxSpeedRule("speed-rule", 80.0f);

        // when & then
        assertThat(rule.getPriority()).isEqualTo(2);
    }

    @Test
    void getThresholdSpeed_returnsConfiguredThreshold() {
        // given
        MaxSpeedRule rule = new MaxSpeedRule("speed-rule", 80.0f);

        // when & then
        assertThat(rule.getThresholdSpeed()).isEqualTo(80.0f);
    }
}
