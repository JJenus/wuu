package com.surf.tracker.alerting.domain.parameters;

import java.util.List;

public record SpeedRuleParameters(
        float speedLimit,
        float buffer,
        String severity,
        List<String> vehicleIds,
        String unit,
        int evaluationInterval
) implements RuleParameters {
}
