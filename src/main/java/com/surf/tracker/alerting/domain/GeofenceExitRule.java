package com.surf.tracker.alerting.domain;

import com.surf.tracker.alerting.domain.enums.AlertSeverity;
import com.surf.tracker.alerting.domain.enums.AlertType;
import com.surf.tracker.shared.domain.LocationPoint;
import java.awt.geom.Path2D;
import java.util.List;

public class GeofenceExitRule implements IAlertRule {
    private final String ruleKey;
    private final String ruleName;
    private final String geofenceId;
    private final List<LocationPoint> boundaryPoints;
    private final boolean enabled;
    private final int priority;
    private boolean wasInside;

    public GeofenceExitRule(String ruleKey, String geofenceId,
                           List<LocationPoint> boundaryPoints) {
        this(ruleKey, geofenceId, boundaryPoints, true);
    }

    public GeofenceExitRule(String ruleKey, String geofenceId,
                           List<LocationPoint> boundaryPoints, boolean enabled) {
        this.ruleKey = ruleKey;
        this.ruleName = "Geofence Exit Rule";
        this.geofenceId = geofenceId;
        this.boundaryPoints = boundaryPoints;
        this.enabled = enabled;
        this.priority = 3;
        this.wasInside = false;
    }
    
    @Override
    public AlertDetectedEvent evaluate(String vehicleId, LocationPoint newLocation) {
        if (!enabled) return null;
        
        boolean isInside = isPointInPolygon(newLocation);
        AlertDetectedEvent alert = null;
        
        if (wasInside && !isInside) {
            String message = String.format(
                "Vehicle %s exited geofence %s",
                vehicleId,
                geofenceId
            );
            
            alert = new AlertDetectedEvent(
                ruleKey, AlertType.GEOFENCE_EXIT,
                vehicleId,
                message,
                AlertSeverity.WARNING,
                newLocation
            );
        }
        
        wasInside = isInside;
        return alert;
    }
    
    private boolean isPointInPolygon(LocationPoint point) {
        if (boundaryPoints.size() < 3) return false;
        
        Path2D polygon = new Path2D.Double();
        polygon.moveTo(boundaryPoints.get(0).longitude(), 
                      boundaryPoints.get(0).latitude());
        
        for (int i = 1; i < boundaryPoints.size(); i++) {
            polygon.lineTo(boundaryPoints.get(i).longitude(), 
                          boundaryPoints.get(i).latitude());
        }
        polygon.closePath();
        
        return polygon.contains(point.longitude(), point.latitude());
    }
    
    @Override
    public String getRuleKey() { return ruleKey; }
    
    @Override
    public String getRuleName() { return ruleName; }
    
    @Override
    public boolean isEnabled() { return enabled; }

    @Override
    public int getPriority() { return priority; }

    public String getGeofenceId() { return geofenceId; }
}
