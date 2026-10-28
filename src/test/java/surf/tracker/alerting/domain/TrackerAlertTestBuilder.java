package com.surf.tracker.alerting.domain;

import com.surf.tracker.alerting.domain.entity.TrackerAlert;
import com.surf.tracker.alerting.domain.enums.AlertSeverity;
import com.surf.tracker.alerting.domain.enums.AlertType;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class TrackerAlertTestBuilder {
    private Long alertId;
    private String trackerId = "tracker-001";
    private String vehicleId = "vehicle-001";
    private AlertType alertType = AlertType.OVERSPEED;
    private AlertSeverity severity = AlertSeverity.WARNING;
    private String message = "Test alert message";
    private String locationId = "40.7128,-74.0060";
    private Instant triggeredAt = Instant.now();
    private Boolean acknowledged = false;
    private String acknowledgedBy;
    private Instant acknowledgedAt;
    private Boolean resolved = false;
    private String resolvedBy;
    private Instant resolvedAt;
    private String resolutionNotes;
    private Map<String, Object> metadata = new HashMap<>();
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();

    private TrackerAlertTestBuilder() {}

    public static TrackerAlertTestBuilder defaultAlert() {
        return new TrackerAlertTestBuilder();
    }

    public static TrackerAlertTestBuilder criticalAlert() {
        return new TrackerAlertTestBuilder()
            .severity(AlertSeverity.CRITICAL)
            .alertType(AlertType.LOW_BATTERY);
    }

    public static TrackerAlertTestBuilder overspeedAlert() {
        return new TrackerAlertTestBuilder()
            .alertType(AlertType.OVERSPEED)
            .message("Vehicle exceeded speed limit")
            .metadata("speedKmh", 120.0f);
    }

    public static TrackerAlertTestBuilder geofenceAlert() {
        return new TrackerAlertTestBuilder()
            .alertType(AlertType.GEOFENCE_VIOLATION)
            .message("Vehicle entered restricted area")
            .metadata("geofenceId", "geofence-001");
    }

    public TrackerAlertTestBuilder alertId(Long alertId) {
        this.alertId = alertId;
        return this;
    }

    public TrackerAlertTestBuilder trackerId(String trackerId) {
        this.trackerId = trackerId;
        return this;
    }

    public TrackerAlertTestBuilder vehicleId(String vehicleId) {
        this.vehicleId = vehicleId;
        return this;
    }

    public TrackerAlertTestBuilder alertType(AlertType alertType) {
        this.alertType = alertType;
        return this;
    }

    public TrackerAlertTestBuilder severity(AlertSeverity severity) {
        this.severity = severity;
        return this;
    }

    public TrackerAlertTestBuilder message(String message) {
        this.message = message;
        return this;
    }

    public TrackerAlertTestBuilder location(String latitude, String longitude) {
        this.locationId = latitude + "," + longitude;
        return this;
    }

    public TrackerAlertTestBuilder acknowledged(boolean acknowledged, String acknowledgedBy) {
        this.acknowledged = acknowledged;
        this.acknowledgedBy = acknowledgedBy;
        this.acknowledgedAt = acknowledged ? Instant.now() : null;
        return this;
    }

    public TrackerAlertTestBuilder resolved(boolean resolved, String resolvedBy, String notes) {
        this.resolved = resolved;
        this.resolvedBy = resolvedBy;
        this.resolvedAt = resolved ? Instant.now() : null;
        this.resolutionNotes = notes;
        return this;
    }

    public TrackerAlertTestBuilder metadata(String key, Object value) {
        this.metadata.put(key, value);
        return this;
    }

    public TrackerAlert build() {
        TrackerAlert alert = new TrackerAlert();
        alert.setAlertId(alertId);
        alert.setTracker(trackerId);
        alert.setVehicle(vehicleId);
        alert.setAlertType(alertType);
        alert.setSeverity(severity);
        alert.setMessage(message);
        alert.setLocation(locationId);
        alert.setTriggeredAt(triggeredAt);
        alert.setAcknowledged(acknowledged);
        alert.setAcknowledgedBy(acknowledgedBy);
        alert.setAcknowledgedAt(acknowledgedAt);
        alert.setResolved(resolved);
        alert.setResolvedBy(resolvedBy);
        alert.setResolvedAt(resolvedAt);
        alert.setResolutionNotes(resolutionNotes);
        alert.setMetadata(metadata);
        alert.setCreatedAt(createdAt);
        alert.setUpdatedAt(updatedAt);
        return alert;
    }
}
