package com.surf.tracker.alerting.domain.parameters;

import java.util.List;

public record IdleRuleParameters(
        int maxIdleMinutes,
        String severity,
        List<String> vehicleIds,
        boolean ignoreEngineOff,
        int notificationThreshold
) implements RuleParameters {
}
