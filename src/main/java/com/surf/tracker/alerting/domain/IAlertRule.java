package com.surf.tracker.alerting.domain;

import com.surf.tracker.shared.domain.LocationPoint;

public interface IAlertRule {
    AlertDetectedEvent evaluate(String vehicleId, LocationPoint newLocation);
    String getRuleKey();
    String getRuleName();
    boolean isEnabled();
    int getPriority();
}

