package com.surf.tracker.alerting.api.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public class CreateAlertRuleRequest {
    
    @NotEmpty(message = "Rule key is required")
    private String ruleKey;
    
    @NotEmpty(message = "Rule name is required")
    private String ruleName;
    
    @NotEmpty(message = "Rule type is required")
    private String ruleType; // SPEED, GEOFENCE, IDLE_TIME
    
    @NotNull(message = "Parameters are required")
    private String parameters; // JSON
    
    @Positive(message = "Priority must be positive")
    private int priority = 5;
    
    private boolean enabled = true;
    
    // Getters and Setters
    public String getRuleKey() { return ruleKey; }
    public void setRuleKey(String ruleKey) { this.ruleKey = ruleKey; }
    
    public String getRuleName() { return ruleName; }
    public void setRuleName(String ruleName) { this.ruleName = ruleName; }
    
    public String getRuleType() { return ruleType; }
    public void setRuleType(String ruleType) { this.ruleType = ruleType; }
    
    public String getParameters() { return parameters; }
    public void setParameters(String parameters) { this.parameters = parameters; }
    
    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }
    
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
