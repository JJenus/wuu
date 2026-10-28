package com.surf.tracker.notification.application.scheduler;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryBackoffTest {

    @Test
    void nextRetryDelay_attemptZero_returnsNearBase() {
        Duration delay = DeliveryBackoff.nextRetryDelay(0);
        assertThat(delay.getSeconds()).isBetween(-1L, 2L);
    }

    @Test
    void nextRetryDelay_attemptFive_returnsPositive() {
        Duration delay = DeliveryBackoff.nextRetryDelay(5);
        assertThat(delay.getSeconds()).isGreaterThan(0L);
    }

    @Test
    void nextRetryDelay_largeAttempt_capsAt360Seconds() {
        Duration delay = DeliveryBackoff.nextRetryDelay(20);
        assertThat(delay.getSeconds()).isLessThanOrEqualTo(360L);
    }
}