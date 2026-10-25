package com.surf.tracker.notification.domain.entity;

import com.surf.tracker.notification.domain.enums.DevicePlatform;
import com.surf.tracker.shared.util.TimeProvider;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "devices", indexes = {
    @Index(name = "idx_device_id", columnList = "deviceId", unique = true),
    @Index(name = "idx_device_user_id", columnList = "userId"),
    @Index(name = "idx_device_push_token", columnList = "pushToken"),
    @Index(name = "idx_device_platform", columnList = "platform")
})
public class Device {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String deviceId;

    @Column
    private String tenantId;

    @Column(nullable = false)
    private String userId;

    @Column
    private String pushToken;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DevicePlatform platform;

    @Column(nullable = false)
    private boolean isValid = true;

    @Column(nullable = false)
    private Instant createdAt = Instant.EPOCH;

    @Column(nullable = false)
    private Instant updatedAt = Instant.EPOCH;

    @PrePersist
    protected void onCreate() {
        if (deviceId == null) {
            deviceId = TimeProvider.newId();
        }
        Instant now = TimeProvider.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = TimeProvider.now();
    }

    public void markInvalid() {
        this.isValid = false;
    }

    public void updatePushToken(String pushToken) {
        this.pushToken = pushToken;
        this.isValid = true;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getPushToken() { return pushToken; }
    public void setPushToken(String pushToken) { this.pushToken = pushToken; }

    public DevicePlatform getPlatform() { return platform; }
    public void setPlatform(DevicePlatform platform) { this.platform = platform; }

    public boolean isValid() { return isValid; }
    public void setValid(boolean valid) { isValid = valid; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
