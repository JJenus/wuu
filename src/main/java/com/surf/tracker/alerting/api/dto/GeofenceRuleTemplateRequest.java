package com.surf.tracker.alerting.api.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.Set;

public class GeofenceRuleTemplateRequest {

    @NotEmpty(message = "Rule key is required")
    @Size(min = 3, max = 100, message = "Rule key must be between 3 and 100 characters")
    private String ruleKey;

    @NotEmpty(message = "Rule name is required")
    @Size(min = 3, max = 200, message = "Rule name must be between 3 and 200 characters")
    private String ruleName;

    @NotEmpty(message = "Geofence ID is required")
    private String geofenceId;

    @NotNull(message = "Action is required")
    private GeofenceAction action = GeofenceAction.BOTH;

    @NotNull(message = "Vehicle IDs are required")
    @NotEmpty(message = "At least one vehicle ID is required")
    private Set<String> vehicleIds;

    @Positive(message = "Priority must be positive")
    private Integer priority = 2;

    private boolean enabled = true;

    public enum GeofenceAction {
        ENTRY, EXIT, BOTH
    }

    // Getters and Setters
    public String getRuleKey() { return ruleKey; }
    public void setRuleKey(String ruleKey) { this.ruleKey = ruleKey; }

    public String getRuleName() { return ruleName; }
    public void setRuleName(String ruleName) { this.ruleName = ruleName; }

    public String getGeofenceId() { return geofenceId; }
    public void setGeofenceId(String geofenceId) { this.geofenceId = geofenceId; }

    public GeofenceAction getAction() { return action; }
    public void setAction(GeofenceAction action) { this.action = action; }

    public Set<String> getVehicleIds() { return vehicleIds; }
    public void setVehicleIds(Set<String> vehicleIds) { this.vehicleIds = vehicleIds; }

    public Integer getPriority() { return priority; }
    public void setPriority(Integer priority) { this.priority = priority; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}