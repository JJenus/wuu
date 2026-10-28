package com.surf.tracker.alerting.domain;

import com.surf.tracker.alerting.domain.enums.AlertSeverity;
import com.surf.tracker.alerting.domain.enums.AlertType;
import com.surf.tracker.shared.domain.LocationPoint;

public class MaxSpeedRule implements IAlertRule {
    private final String ruleKey;
    private final String ruleName;
    private final float thresholdSpeed;
    private final boolean enabled;
    private final int priority;

    public MaxSpeedRule(String ruleKey, float thresholdSpeed) {
        this(ruleKey, "MAX_SPEED_RULE", thresholdSpeed, true, 2);
    }

    public MaxSpeedRule(String ruleKey, String ruleName, float speedLimit) {
        this(ruleKey, ruleName, speedLimit, true, 2);
    }

    public MaxSpeedRule(String ruleKey, String ruleName, float speedLimit, boolean enabled, int priority) {
        this.ruleKey = ruleKey;
        this.ruleName = ruleName;
        this.thresholdSpeed = speedLimit;
        this.enabled = enabled;
        this.priority = priority;
    }

    @Override
    public AlertDetectedEvent evaluate(String vehicleId, LocationPoint newLocation) {
        if (!enabled || vehicleId == null || newLocation == null) {
            return null;
        }

        float speed = newLocation.speedKmh();

        if (speed > thresholdSpeed) {
            String message = String.format(
                    "Vehicle %s exceeded speed limit of %.1f km/h. Current speed: %.1f km/h at %s",
                    vehicleId,
                    thresholdSpeed,
                    speed,
                    formatCoordinates(newLocation.latitude(), newLocation.longitude())
            );

            AlertSeverity severity = speed > thresholdSpeed * 1.5 ?
                    AlertSeverity.CRITICAL : AlertSeverity.WARNING;

            return new AlertDetectedEvent(
                    ruleKey,
                    AlertType.OVERSPEED,
                    vehicleId,
                    message,
                    severity,
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

    public float getThresholdSpeed() { return thresholdSpeed; }
}