package com.surf.tracker.alerting.domain;

import com.surf.tracker.alerting.domain.entity.AlertRule;
import com.surf.tracker.alerting.domain.enums.AlertRuleType;
import java.time.Instant;
import java.util.*;

public class AlertRuleTestBuilder {
    private Long ruleId;
    private String ruleKey;
    private String ruleName;
    private AlertRuleType ruleType;
    private Boolean isEnabled = true;
    private Integer priority = 5;
    private Map<String, Object> parameters = new HashMap<>();
    private Set<String> vehicleIds = new HashSet<>();
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();
    private Long organizationId = 1L;

    private AlertRuleTestBuilder() {}

    public static AlertRuleTestBuilder defaultRule() {
        return new AlertRuleTestBuilder()
            .ruleKey("test-rule-key")
            .ruleName("Test Rule")
            .ruleType(AlertRuleType.SPEED);
    }

    public static AlertRuleTestBuilder overspeedRule() {
        return new AlertRuleTestBuilder()
            .ruleKey("overspeed-rule-001")
            .ruleName("Overspeed Alert")
            .ruleType(AlertRuleType.SPEED)
            .parameter("speedLimit", 80.0f)
            .parameter("buffer", 5.0f);
    }

    public static AlertRuleTestBuilder idleTimeoutRule() {
        return new AlertRuleTestBuilder()
            .ruleKey("idle-rule-001")
            .ruleName("Idle Timeout Alert")
            .ruleType(AlertRuleType.TIME)
            .parameter("maxIdleMinutes", 30);
    }

    public static AlertRuleTestBuilder geofenceRule() {
        return new AlertRuleTestBuilder()
            .ruleKey("geofence-rule-001")
            .ruleName("Geofence Alert")
            .ruleType(AlertRuleType.GEOFENCE)
            .parameter("geofenceId", "123");
    }

    public AlertRuleTestBuilder ruleKey(String ruleKey) {
        this.ruleKey = ruleKey;
        return this;
    }

    public AlertRuleTestBuilder ruleName(String ruleName) {
        this.ruleName = ruleName;
        return this;
    }

    public AlertRuleTestBuilder ruleType(AlertRuleType ruleType) {
        this.ruleType = ruleType;
        return this;
    }

    public AlertRuleTestBuilder enabled(boolean enabled) {
        this.isEnabled = enabled;
        return this;
    }

    public AlertRuleTestBuilder priority(int priority) {
        this.priority = priority;
        return this;
    }

    public AlertRuleTestBuilder parameter(String key, Object value) {
        this.parameters.put(key, value);
        return this;
    }

    public AlertRuleTestBuilder vehicleId(String vehicleId) {
        this.vehicleIds.add(vehicleId);
        return this;
    }

    public AlertRuleTestBuilder vehicleIds(Set<String> vehicleIds) {
        this.vehicleIds = vehicleIds;
        return this;
    }

    public AlertRuleTestBuilder organizationId(Long organizationId) {
        this.organizationId = organizationId;
        return this;
    }

    public AlertRule build() {
        AlertRule rule = new AlertRule();
        rule.setRuleId(ruleId);
        rule.setRuleKey(ruleKey);
        rule.setRuleName(ruleName);
        rule.setRuleType(ruleType);
        rule.setIsEnabled(isEnabled);
        rule.setPriority(priority);
        rule.setParameters(parameters);
        rule.setVehicleIds(vehicleIds);
        rule.setOrganizationId(organizationId);
        rule.setCreatedAt(createdAt);
        rule.setUpdatedAt(updatedAt);
        return rule;
    }
}
