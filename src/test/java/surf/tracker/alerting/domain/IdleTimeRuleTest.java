package com.surf.tracker.alerting.domain;

import com.surf.tracker.shared.domain.LocationPoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class IdleTimeRuleTest {

    @Test
    void evaluate_vehicleMoving_returnsNull() {
        // given
        IdleTimeRule rule = new IdleTimeRule("idle-rule", "Idle Alert", Duration.ofMinutes(30));
        LocationPoint location = LocationPointTestBuilder.defaultLocation()
            .speedKmh(10.0f)
            .build();

        // when
        var result = rule.evaluate("vehicle-001", location);

        // then
        assertThat(result).isNull();
    }

    @Test
    void evaluate_vehicleIdleWithinLimit_returnsNull() {
        // given
        IdleTimeRule rule = new IdleTimeRule("idle-rule", "Idle Alert", Duration.ofMinutes(30));
        LocationPoint location = LocationPointTestBuilder.stationaryLocation()
            .build();

        // when
        var result = rule.evaluate("vehicle-001", location);

        // then
        assertThat(result).isNull();
    }

    @Test
    void evaluate_vehicleExceedsIdleLimit_returnsAlert() throws InterruptedException {
        // given
        Duration maxIdle = Duration.ofSeconds(1); // 1 second for testing
        IdleTimeRule rule = new IdleTimeRule("idle-rule", "Idle Alert", maxIdle);

        // First location sets last movement time
        rule.evaluate("vehicle-001", LocationPointTestBuilder.stationaryLocation().build());

        // Wait for idle time to exceed limit
        Thread.sleep(1500);

        LocationPoint secondLocation = LocationPointTestBuilder.stationaryLocation()
            .build();

        // when
        var result = rule.evaluate("vehicle-001", secondLocation);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getMessage()).contains("has been idle");
    }

    @Test
    void evaluate_vehicleMovesAfterBeingIdle_resetsTimer() throws InterruptedException {
        // given
        Duration maxIdle = Duration.ofSeconds(1);
        IdleTimeRule rule = new IdleTimeRule("idle-rule", "Idle Alert", maxIdle);

        // Start idle
        rule.evaluate("vehicle-001", LocationPointTestBuilder.stationaryLocation().build());
        Thread.sleep(500);

        // Move vehicle (should reset timer)
        rule.evaluate("vehicle-001", LocationPointTestBuilder.defaultLocation().speedKmh(5.0f).build());

        // Wait less than max idle time
        Thread.sleep(500);
        LocationPoint thirdLocation = LocationPointTestBuilder.stationaryLocation().build();

        // when
        var result = rule.evaluate("vehicle-001", thirdLocation);

        // then
        assertThat(result).isNull();
    }

    @Test
    void evaluate_disabledRule_returnsNull() {
        // given
        IdleTimeRule rule = new IdleTimeRule("idle-rule", "Idle Alert", Duration.ofMinutes(30), false, 1);
        LocationPoint location = LocationPointTestBuilder.stationaryLocation().build();

        // when
        var result = rule.evaluate("vehicle-001", location);

        // then
        assertThat(result).isNull();
    }

    @Test
    void getMaxIdleTime_returnsConfiguredDuration() {
        // given
        Duration maxIdle = Duration.ofMinutes(30);
        IdleTimeRule rule = new IdleTimeRule("idle-rule", "Idle Alert", maxIdle);

        // when & then
        assertThat(rule.getMaxIdleTime()).isEqualTo(maxIdle);
    }

    @Test
    void getPriority_returnsDefaultPriority() {
        // given
        IdleTimeRule rule = new IdleTimeRule("idle-rule", "Idle Alert", Duration.ofMinutes(30));

        // when & then
        assertThat(rule.getPriority()).isEqualTo(1);
    }
}
