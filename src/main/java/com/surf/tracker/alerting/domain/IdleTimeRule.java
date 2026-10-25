package com.surf.tracker.alerting.domain;

import com.surf.tracker.alerting.domain.enums.AlertSeverity;
import com.surf.tracker.alerting.domain.enums.AlertType;
import com.surf.tracker.shared.domain.LocationPoint;
import com.surf.tracker.shared.util.TimeProvider;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

public class IdleTimeRule implements IAlertRule {
    private final String ruleKey;
    private final String ruleName;
    private final Duration maxIdleTime;
    private final boolean enabled;
    private final int priority;

    private final Map<String, Instant> lastMovementTimes;
    private final BiConsumer<String, Instant> persistTime;

    public IdleTimeRule(String ruleKey, String ruleName, Duration maxIdleTime) {
        this(ruleKey, ruleName, maxIdleTime, true, 1,
             new HashMap<>(),
             (v, t) -> {});
    }

    public IdleTimeRule(String ruleKey, String ruleName, Duration maxIdleTime,
                       boolean enabled, int priority) {
        this(ruleKey, ruleName, maxIdleTime, enabled, priority,
             new HashMap<>(),
             (v, t) -> {});
    }

    public IdleTimeRule(String ruleKey, String ruleName, Duration maxIdleTime,
                       Map<String, Instant> persistedTimes,
                       BiConsumer<String, Instant> persistTime) {
        this(ruleKey, ruleName, maxIdleTime, true, 1, persistedTimes, persistTime);
    }

    public IdleTimeRule(String ruleKey, String ruleName, Duration maxIdleTime,
                       boolean enabled, int priority,
                       Map<String, Instant> persistedTimes,
                       BiConsumer<String, Instant> persistTime) {
        this.ruleKey = ruleKey;
        this.ruleName = ruleName;
        this.maxIdleTime = maxIdleTime;
        this.enabled = enabled;
        this.priority = priority;
        this.lastMovementTimes = new HashMap<>(persistedTimes);
        this.persistTime = persistTime;
    }

    @Override
    public AlertDetectedEvent evaluate(String vehicleId, LocationPoint newLocation) {
        if (!enabled) return null;

        Instant now = TimeProvider.now();
        Instant lastMovement = lastMovementTimes.get(vehicleId);

        if (newLocation.speedKmh() > 1.0) {
            lastMovementTimes.put(vehicleId, now);
            persistTime.accept(vehicleId, now);
            return null;
        }

        if (lastMovement == null) {
            lastMovementTimes.put(vehicleId, now);
            persistTime.accept(vehicleId, now);
            return null;
        }

        Duration idleDuration = Duration.between(lastMovement, now);

        if (idleDuration.compareTo(maxIdleTime) > 0) {
            String message = String.format(
                    "Vehicle %s has been idle for %d minutes (max allowed: %d minutes) at %s",
                    vehicleId,
                    idleDuration.toMinutes(),
                    maxIdleTime.toMinutes(),
                    formatCoordinates(newLocation.latitude(), newLocation.longitude())
            );

            return new AlertDetectedEvent(
                    ruleKey,
                    AlertType.IDLE_TIMEOUT,
                    vehicleId,
                    message,
                    AlertSeverity.INFO,
                    newLocation
            );
        }
        return null;
    }

    private String formatCoordinates(double lat, double lon) {
        return String.format("[%.6f, %.6f]", lat, lon);
    }

    @Override
    public String getRuleKey() { return ruleKey; }

    @Override
    public String getRuleName() { return ruleName; }

    @Override
    public boolean isEnabled() { return enabled; }

    @Override
    public int getPriority() { return priority; }

    public Duration getMaxIdleTime() { return maxIdleTime; }
}