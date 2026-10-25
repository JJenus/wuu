package com.surf.tracker.notification.domain.entity;

import com.surf.tracker.notification.domain.enums.NotificationChannel;
import com.surf.tracker.shared.util.TimeProvider;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "notification_preferences",
       uniqueConstraints = @UniqueConstraint(columnNames = {"tenantId", "userId", "category"}))
public class NotificationPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column
    private String tenantId;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private String category;

    @ElementCollection
    @CollectionTable(name = "preference_channels",
                     joinColumns = @JoinColumn(name = "preference_id"))
    @Column(name = "channel")
    @Enumerated(EnumType.STRING)
    private Set<NotificationChannel> enabledChannels = new HashSet<>();

    private boolean enabled = true;

    @Column(nullable = false)
    private Instant createdAt = Instant.EPOCH;

    @Column(nullable = false)
    private Instant updatedAt = Instant.EPOCH;

    @PreUpdate
    protected void onUpdate() {
        updatedAt = TimeProvider.now();
    }

    public void enableChannel(NotificationChannel channel) {
        enabledChannels.add(channel);
    }

    public void disableChannel(NotificationChannel channel) {
        enabledChannels.remove(channel);
    }

    public boolean isChannelEnabled(NotificationChannel channel) {
        return enabled && enabledChannels.contains(channel);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public Set<NotificationChannel> getEnabledChannels() { return enabledChannels; }
    public void setEnabledChannels(Set<NotificationChannel> enabledChannels) {
        this.enabledChannels = enabledChannels;
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}