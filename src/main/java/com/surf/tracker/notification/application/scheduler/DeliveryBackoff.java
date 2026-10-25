package com.surf.tracker.notification.application.scheduler;

import java.time.Duration;

public final class DeliveryBackoff {

    private DeliveryBackoff() {
    }

    public static Duration nextRetryDelay(int attemptCount) {
        int base = (int) Math.min(Math.pow(2, attemptCount), 300);
        double jitter = base * 0.2 * (Math.random() * 2 - 1);
        return Duration.ofSeconds((long)(base + jitter));
    }
}