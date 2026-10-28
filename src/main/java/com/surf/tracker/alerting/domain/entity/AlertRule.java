package com.surf.tracker.alerting.domain.entity;

import com.surf.tracker.alerting.domain.enums.AlertRuleType;
import com.surf.tracker.shared.util.TimeProvider;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "alert_rules")
public class AlertRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "rule_id")
    private Long ruleId;

    @Column(name = "rule_key", length = 100, unique = true, nullable = false)
    private String ruleKey;

    @Column(name = "rule_name", length = 200, nullable = false)
    private String ruleName;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type", length = 50, nullable = false)
    private AlertRuleType ruleType;

    @Column(name = "is_enabled")
    private Boolean isEnabled = true;

    @Column(name = "priority")
    private Integer priority = 5;

    @Column(name = "parameters", columnDefinition = "JSONB", nullable = false)
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> parameters = new HashMap<>();

    @ElementCollection
    @CollectionTable(
            name = "alert_rule_vehicles",
            joinColumns = @JoinColumn(name = "rule_id")
    )
    @Column(name = "vehicle_id")
    private Set<String> vehicleIds = new HashSet<>();

    @Column(name = "conditions", columnDefinition = "JSONB")
    @JdbcTypeCode(SqlTypes.JSON)
    private String conditions;

    @Column(name = "actions", columnDefinition = "JSONB")
    @JdbcTypeCode(SqlTypes.JSON)
    private String actions;

    @Column(name = "notification_channels", columnDefinition = "JSONB")
    @JdbcTypeCode(SqlTypes.JSON)
    private String notificationChannels;

    @Column(name = "cooldown_minutes")
    private Integer cooldownMinutes = 5;

    @Column(name = "organization_id")
    private Long organizationId;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "created_at")
    private Instant createdAt = Instant.EPOCH;

    @Column(name = "updated_at")
    private Instant updatedAt = Instant.EPOCH;

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = TimeProvider.now();
    }

    // Business methods
    public boolean isInCooldown(Instant lastTriggered) {
        if (cooldownMinutes == null || cooldownMinutes == 0) return false;
        if (lastTriggered == null) return false;

        return TimeProvider.now().isBefore(
                lastTriggered.plusSeconds(cooldownMinutes * 60L)
        );
    }

    public boolean appliesToVehicle(String vehicleId) {
        return vehicleIds != null && vehicleIds.contains(vehicleId);
    }

    // Getters and Setters
    public Long getRuleId() { return ruleId; }
    public void setRuleId(Long ruleId) { this.ruleId = ruleId; }

    public String getRuleKey() { return ruleKey; }
    public void setRuleKey(String ruleKey) { this.ruleKey = ruleKey; }

    public String getRuleName() { return ruleName; }
    public void setRuleName(String ruleName) { this.ruleName = ruleName; }

    public AlertRuleType getRuleType() { return ruleType; }
    public void setRuleType(AlertRuleType ruleType) { this.ruleType = ruleType; }

    public Boolean isEnabled() { return isEnabled; }
    public void setIsEnabled(Boolean isEnabled) { this.isEnabled = isEnabled; }

    public Integer getPriority() { return priority; }
    public void setPriority(Integer priority) { this.priority = priority; }

    public Map<String, Object> getParameters() { return parameters; }
    public void setParameters(Map<String, Object> parameters) { this.parameters = parameters; }

    public Set<String> getVehicleIds() { return vehicleIds; }
    public void setVehicleIds(Set<String> vehicleIds) { this.vehicleIds = vehicleIds; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public String getConditions() { return conditions; }
    public void setConditions(String conditions) { this.conditions = conditions; }

    public String getActions() { return actions; }
    public void setActions(String actions) { this.actions = actions; }

    public String getNotificationChannels() { return notificationChannels; }
    public void setNotificationChannels(String notificationChannels) {
        this.notificationChannels = notificationChannels;
    }

    public Integer getCooldownMinutes() { return cooldownMinutes; }
    public void setCooldownMinutes(Integer cooldownMinutes) { this.cooldownMinutes = cooldownMinutes; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public Long getOrganizationId() { return organizationId; }
    public void setOrganizationId(Long organizationId) { this.organizationId = organizationId; }
}
