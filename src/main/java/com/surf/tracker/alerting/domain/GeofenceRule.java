package com.surf.tracker.alerting.domain;

import com.surf.tracker.alerting.domain.enums.AlertSeverity;
import com.surf.tracker.alerting.domain.enums.AlertType;
import com.surf.tracker.shared.domain.LocationPoint;
import com.surf.tracker.shared.util.TimeProvider;
import java.awt.geom.Path2D;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;

public class GeofenceRule implements IAlertRule {
    public enum Action {
        ENTRY, EXIT, BOTH
    }

    private final String ruleKey;
    private final String ruleName;
    private final String geofenceId;
    private final List<LocationPoint> boundaryPoints;
    private final Action action;
    private final boolean enabled;
    private final int priority;
    private final long maxDwellMinutes;

    private boolean wasInside = false;
    private Instant entryTime;

    private final java.util.function.Function<String, Optional<Instant>> entryTimeLoader;
    private final BiConsumer<String, Instant> entryTimePersister;
    private final Runnable entryTimeClearer;

    public GeofenceRule(String ruleKey, String ruleName, String geofenceId,
                        List<LocationPoint> boundaryPoints, Action action, int priority) {
        this(ruleKey, ruleName, geofenceId, boundaryPoints, action, priority, true, 0L,
             id -> Optional.empty(), (v, t) -> {}, () -> {});
    }

    public GeofenceRule(String ruleKey, String ruleName, String geofenceId,
                        List<LocationPoint> boundaryPoints, Action action, int priority, boolean enabled) {
        this(ruleKey, ruleName, geofenceId, boundaryPoints, action, priority, enabled, 0L,
             id -> Optional.empty(), (v, t) -> {}, () -> {});
    }

    public GeofenceRule(String ruleKey, String ruleName, String geofenceId,
                        List<LocationPoint> boundaryPoints, Action action, int priority,
                        long maxDwellMinutes,
                        java.util.function.Function<String, Optional<Instant>> entryTimeLoader,
                        BiConsumer<String, Instant> entryTimePersister,
                        Runnable entryTimeClearer) {
        this(ruleKey, ruleName, geofenceId, boundaryPoints, action, priority, true,
             maxDwellMinutes, entryTimeLoader, entryTimePersister, entryTimeClearer);
    }

    public GeofenceRule(String ruleKey, String ruleName, String geofenceId,
                        List<LocationPoint> boundaryPoints, Action action, int priority,
                        boolean enabled, long maxDwellMinutes,
                        java.util.function.Function<String, Optional<Instant>> entryTimeLoader,
                        BiConsumer<String, Instant> entryTimePersister,
                        Runnable entryTimeClearer) {
        this.ruleKey = ruleKey;
        this.ruleName = ruleName;
        this.geofenceId = geofenceId;
        this.boundaryPoints = boundaryPoints;
        this.action = action;
        this.enabled = enabled;
        this.priority = priority;
        this.maxDwellMinutes = maxDwellMinutes;
        this.entryTimeLoader = entryTimeLoader;
        this.entryTimePersister = entryTimePersister;
        this.entryTimeClearer = entryTimeClearer;
    }

    @Override
    public AlertDetectedEvent evaluate(String vehicleId, LocationPoint newLocation) {
        if (!enabled || boundaryPoints == null || boundaryPoints.size() < 3) {
            return null;
        }

        boolean isInside = isPointInPolygon(newLocation);
        AlertDetectedEvent alert = null;
        Instant now = TimeProvider.now();

        switch (action) {
            case ENTRY:
                if (!wasInside && isInside) {
                    alert = createEntryAlert(vehicleId, newLocation);
                    recordEntry(vehicleId, now);
                } else if (wasInside && isInside) {
                    alert = checkDwell(vehicleId, newLocation, now);
                } else if (wasInside && !isInside) {
                    clearEntry(vehicleId);
                }
                break;

            case EXIT:
                if (wasInside && !isInside) {
                    alert = createExitAlert(vehicleId, newLocation);
                    clearEntry(vehicleId);
                }
                break;

            case BOTH:
                if (!wasInside && isInside) {
                    alert = createEntryAlert(vehicleId, newLocation);
                    recordEntry(vehicleId, now);
                } else if (wasInside && isInside) {
                    alert = checkDwell(vehicleId, newLocation, now);
                } else if (wasInside && !isInside) {
                    alert = createExitAlert(vehicleId, newLocation);
                    clearEntry(vehicleId);
                }
                break;
        }

        wasInside = isInside;
        return alert;
    }

    private void recordEntry(String vehicleId, Instant now) {
        entryTime = now;
        entryTimePersister.accept(vehicleId, now);
    }

    private void clearEntry(String vehicleId) {
        entryTime = null;
        entryTimeClearer.run();
    }

    private AlertDetectedEvent checkDwell(String vehicleId, LocationPoint location, Instant now) {
        if (maxDwellMinutes <= 0) {
            return null;
        }
        Instant entry = entryTime != null ? entryTime : entryTimeLoader.apply(vehicleId).orElse(null);
        if (entry == null) {
            return null;
        }
        Duration dwell = Duration.between(entry, now);
        if (dwell.toMinutes() >= maxDwellMinutes) {
            String message = String.format(
                    "Vehicle %s has been inside geofence %s for %d minutes (max allowed: %d minutes)",
                    vehicleId, geofenceId, dwell.toMinutes(), maxDwellMinutes
            );
            return new AlertDetectedEvent(
                    ruleKey,
                    AlertType.GEOFENCE_DWELL_EXCEEDED,
                    vehicleId,
                    message,
                    AlertSeverity.WARNING,
                    location
            );
        }
        return null;
    }

    private AlertDetectedEvent createEntryAlert(String vehicleId, LocationPoint location) {
        String message = String.format(
                "Vehicle %s entered geofence %s at %s",
                vehicleId,
                geofenceId,
                formatCoordinates(location.latitude(), location.longitude())
        );
        return new AlertDetectedEvent(
                ruleKey,
                AlertType.GEOFENCE_ENTRY,
                vehicleId,
                message,
                AlertSeverity.INFO,
                location
        );
    }

    private AlertDetectedEvent createExitAlert(String vehicleId, LocationPoint location) {
        String message = String.format(
                "Vehicle %s exited geofence %s at %s",
                vehicleId,
                geofenceId,
                formatCoordinates(location.latitude(), location.longitude())
        );
        return new AlertDetectedEvent(
                ruleKey,
                AlertType.GEOFENCE_EXIT,
                vehicleId,
                message,
                AlertSeverity.WARNING,
                location
        );
    }

    private String formatCoordinates(double lat, double lon) {
        return String.format("[%.6f, %.6f]", lat, lon);
    }

    private boolean isPointInPolygon(LocationPoint point) {
        try {
            Path2D polygon = new Path2D.Double();
            polygon.moveTo(boundaryPoints.get(0).longitude(),
                    boundaryPoints.get(0).latitude());

            for (int i = 1; i < boundaryPoints.size(); i++) {
                polygon.lineTo(boundaryPoints.get(i).longitude(),
                        boundaryPoints.get(i).latitude());
            }
            polygon.closePath();

            return polygon.contains(point.longitude(), point.latitude());
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String getRuleKey() { return ruleKey; }

    @Override
    public String getRuleName() { return ruleName; }

    @Override
    public boolean isEnabled() { return enabled; }

    @Override
    public int getPriority() { return priority; }

    public boolean isWasInside() { return wasInside; }

    public void setWasInside(boolean wasInside) { this.wasInside = wasInside; }

    public long getMaxDwellMinutes() { return maxDwellMinutes; }

    public Instant getEntryTime() { return entryTime; }

    // Additional methods
    public String getGeofenceId() { return geofenceId; }
    public Action getAction() { return action; }
    public List<LocationPoint> getBoundaryPoints() { return boundaryPoints; }
}