package com.surf.tracker.alerting.domain;

import com.surf.tracker.alerting.domain.entity.AlertRule;
import com.surf.tracker.shared.domain.LocationPoint;
import java.util.Map;

public class GenericAlertRule implements IAlertRule {
    private final String ruleKey;
    private final String ruleName;
    private final Map<String, Object> parameters;
    private final boolean enabled;
    private final int priority;

    public GenericAlertRule(AlertRule entity, Map<String, Object> parameters) {
        this.ruleKey = entity.getRuleKey();
        this.ruleName = entity.getRuleName();
        this.parameters = parameters;
        this.enabled = Boolean.TRUE.equals(entity.isEnabled());
        this.priority = entity.getPriority();
    }

    @Override
    public AlertDetectedEvent evaluate(String vehicleId, LocationPoint newLocation) {
        return null;
    }

    @Override
    public String getRuleKey() { return ruleKey; }

    @Override
    public String getRuleName() { return ruleName; }

    @Override
    public boolean isEnabled() { return enabled; }

    @Override
    public int getPriority() { return priority; }

    public Map<String, Object> getParameters() { return parameters; }
}