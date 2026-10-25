package com.surf.tracker.alerting.api.dto;

import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.Map;

public class UpdateAlertRuleRequest {

    @Size(min = 3, max = 200, message = "Rule name must be between 3 and 200 characters")
    private String ruleName;

    @Size(min = 3, max = 100, message = "Rule key must be between 3 and 100 characters")
    private String ruleKey;

    private Map<String, Object> parameters;

    @PositiveOrZero(message = "Priority must be zero or positive")
    private int priority = -1; // -1 means not updating

    // Getters and Setters
    public String getRuleName() { return ruleName; }
    public void setRuleName(String ruleName) { this.ruleName = ruleName; }

    public String getRuleKey() { return ruleKey; }
    public void setRuleKey(String ruleKey) { this.ruleKey = ruleKey; }

    public Map<String, Object> getParameters() { return parameters; }
    public void setParameters(Map<String, Object> parameters) { this.parameters = parameters; }

    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }
}