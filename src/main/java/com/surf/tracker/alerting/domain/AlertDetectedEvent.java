package com.surf.tracker.alerting.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.surf.tracker.alerting.domain.enums.AlertSeverity;
import com.surf.tracker.alerting.domain.enums.AlertType;
import com.surf.tracker.shared.domain.LocationPoint;
import com.surf.tracker.shared.pubsub.DomainEvent;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

public class AlertDetectedEvent extends DomainEvent {
    private final String ruleKey;
    private final AlertType alertType;
    private final String vehicleId;
    private final String message;
    private final AlertSeverity severity;
    private final Instant alertTimestamp;
    private final LocationPoint location;
    private final int cooldownMinutes;

    @JsonCreator
    public AlertDetectedEvent(@JsonProperty("event_id") String eventId,
                              @JsonProperty("occurred_on") Instant occurredOn,
                              @JsonProperty("rule_key") String ruleKey,
                              @JsonProperty("alert_type") AlertType alertType,
                              @JsonProperty("vehicle_id") String vehicleId,
                              @JsonProperty("message") String message,
                              @JsonProperty("severity") AlertSeverity severity,
                              @JsonProperty("alert_timestamp") Instant alertTimestamp,
                              @JsonProperty("location") LocationPoint location,
                              @JsonProperty("cooldown_minutes") int cooldownMinutes) {
        super(eventId, occurredOn);
        this.ruleKey = ruleKey;
        this.alertType = alertType;
        this.vehicleId = vehicleId;
        this.message = message;
        this.severity = severity;
        this.alertTimestamp = alertTimestamp;
        this.location = location;
        this.cooldownMinutes = cooldownMinutes;
    }

    public AlertDetectedEvent(Clock clock, String ruleKey, AlertType alertType, String vehicleId,
                              String message, AlertSeverity severity, LocationPoint location) {
        this(clock, ruleKey, alertType, vehicleId, message, severity, location, 5);
    }

    public AlertDetectedEvent(Clock clock, String ruleKey, AlertType alertType, String vehicleId,
                              String message, AlertSeverity severity, LocationPoint location,
                              int cooldownMinutes) {
        super(clock, UUID.randomUUID());
        this.ruleKey = ruleKey;
        this.alertType = alertType;
        this.vehicleId = vehicleId;
        this.message = message;
        this.severity = severity;
        this.alertTimestamp = clock.instant();
        this.location = location;
        this.cooldownMinutes = cooldownMinutes;
    }

    public AlertDetectedEvent(String ruleKey, AlertType alertType, String vehicleId, String message,
                              AlertSeverity severity, LocationPoint location) {
        this(Clock.systemUTC(), ruleKey, alertType, vehicleId, message, severity, location);
    }

    public AlertDetectedEvent(String ruleKey, AlertType alertType, String vehicleId, String message,
                              AlertSeverity severity, LocationPoint location, int cooldownMinutes) {
        this(Clock.systemUTC(), ruleKey, alertType, vehicleId, message, severity, location, cooldownMinutes);
    }

    public String getRuleKey() { return ruleKey; }
    public String getVehicleId() { return vehicleId; }
    public String getMessage() { return message; }
    public AlertType getAlertType() { return alertType; }
    public AlertSeverity getSeverity() { return severity; }
    public Instant getAlertTimestamp() { return alertTimestamp; }
    public LocationPoint getLocation() { return location; }
    public int getCooldownMinutes() { return cooldownMinutes; }
}
