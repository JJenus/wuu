package com.surf.tracker.alerting.domain.parameters;

import java.util.List;

public record GeofenceRuleParameters(
        String geofenceId,
        String geofenceName,
        String action,
        String severity,
        List<String> vehicleIds,
        String shapeType,
        Double centerLatitude,
        Double centerLongitude,
        Integer radiusMeters,
        long maxDwellMinutes
) implements RuleParameters {
}
