package com.surf.tracker.notification.domain.entity;

import com.surf.tracker.notification.domain.enums.NotificationStatus;
import com.surf.tracker.shared.util.TimeProvider;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "notification_hubs", indexes = {
    @Index(name = "idx_hub_notification_id", columnList = "notificationId", unique = true),
    @Index(name = "idx_hub_alert_id", columnList = "alertId"),
    @Index(name = "idx_hub_user_id", columnList = "userId"),
    @Index(name = "idx_hub_status", columnList = "status"),
    @Index(name = "idx_hub_created", columnList = "createdAt DESC")
})
public class NotificationHub {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String notificationId;

    @Column
    private String tenantId;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private String templateId;

    @Column
    private String category;

    @Column
    private String alertId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationStatus status = NotificationStatus.CREATED;

    @Column(columnDefinition = "TEXT")
    private String metadata;

    @Column(nullable = false)
    private Instant createdAt = Instant.EPOCH;

    @Column(nullable = false)
    private Instant updatedAt = Instant.EPOCH;

    @OneToMany(mappedBy = "notificationHub", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Delivery> deliveries = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        if (notificationId == null) {
            notificationId = TimeProvider.newId();
        }
        Instant now = TimeProvider.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = TimeProvider.now();
    }

    public void addDelivery(Delivery delivery) {
        deliveries.add(delivery);
        delivery.setNotificationHub(this);
    }

    public void markProcessing() {
        this.status = NotificationStatus.PROCESSING;
    }

    public void markCompleted() {
        this.status = NotificationStatus.COMPLETED;
    }

    public void markPartialFailure() {
        this.status = NotificationStatus.PARTIAL_FAILURE;
    }

    public void markFailed() {
        this.status = NotificationStatus.FAILED;
    }

    public void recalculateStatus() {
        if (deliveries.isEmpty()) {
            this.status = NotificationStatus.CREATED;
            return;
        }

        boolean anySent = false;
        boolean anyFailed = false;
        boolean anyExhausted = false;

        for (Delivery delivery : deliveries) {
            switch (delivery.getStatus()) {
                case SENT, DELIVERED -> anySent = true;
                case FAILED -> anyFailed = true;
                case EXHAUSTED -> anyExhausted = true;
            }
        }

        if (anyExhausted || anyFailed) {
            if (anySent) {
                this.status = NotificationStatus.PARTIAL_FAILURE;
            } else {
                this.status = NotificationStatus.FAILED;
            }
        } else if (anySent) {
            boolean allFinal = deliveries.stream()
                .allMatch(d -> d.getStatus().isFinal());
            this.status = allFinal ? NotificationStatus.COMPLETED : NotificationStatus.PROCESSING;
        } else {
            this.status = NotificationStatus.PROCESSING;
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNotificationId() { return notificationId; }
    public void setNotificationId(String notificationId) { this.notificationId = notificationId; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getTemplateId() { return templateId; }
    public void setTemplateId(String templateId) { this.templateId = templateId; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getAlertId() { return alertId; }
    public void setAlertId(String alertId) { this.alertId = alertId; }

    public NotificationStatus getStatus() { return status; }
    public void setStatus(NotificationStatus status) { this.status = status; }

    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public List<Delivery> getDeliveries() { return deliveries; }
    public void setDeliveries(List<Delivery> deliveries) { this.deliveries = deliveries; }
}
