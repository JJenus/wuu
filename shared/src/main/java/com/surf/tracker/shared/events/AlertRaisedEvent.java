package com.surf.tracker.shared.events;

import com.surf.tracker.shared.pubsub.DomainEvent;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public class AlertRaisedEvent extends DomainEvent {
    private final String alertId;
    private final String ruleKey;
    private final String vehicleId;
    private final String alertType;
    private final String severity;
    private final String message;
    private final Instant timestamp;
    private final Double latitude;
    private final Double longitude;
    private final Double speed;
    private final Map<String,Object> metadata;

public AlertRaisedEvent(
        Clock clock,
        String alertId,
        String ruleKey,
        String vehicleId,
        String alertType,
        String severity,
        String message,
        Instant timestamp,
        Double latitude,
        Double longitude,
        Double speed,
        Map<String, Object> metadata
) {
    super(clock, UUID.randomUUID());
    this.alertId = alertId;
    this.ruleKey = ruleKey;
    this.vehicleId = vehicleId;
    this.alertType = alertType;
    this.severity = severity;
    this.message = message;
    this.timestamp = timestamp;
    this.latitude = latitude;
    this.longitude = longitude;
    this.speed = speed;
    this.metadata = metadata;
}

    public AlertRaisedEvent(
            String alertId,
            String ruleKey,
            String vehicleId,
            String alertType,
            String severity,
            String message,
            Instant timestamp,
            Double latitude,
            Double longitude,
            Double speed,
            Map<String, Object> metadata
    ) {
        this(Clock.systemUTC(), alertId, ruleKey, vehicleId, alertType, severity,
                message, timestamp, latitude, longitude, speed, metadata);
    }

    public String getAlertId() { return alertId; }
    public String getRuleKey() { return ruleKey; }
    public String getVehicleId() { return vehicleId; }
    public String getAlertType() { return alertType; }
    public String getSeverity() { return severity; }
    public String getMessage() { return message; }
    public Instant getTimestamp() { return timestamp; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public Double getSpeed() { return speed; }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    @Override
    public String toString() {
        return "AlertRaisedEvent{" +
                "alertId='" + alertId + '\'' +
                ", ruleKey='" + ruleKey + '\'' +
                ", vehicleId='" + vehicleId + '\'' +
                ", ruleType='" + alertType + '\'' +
                ", severity='" + severity + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}
