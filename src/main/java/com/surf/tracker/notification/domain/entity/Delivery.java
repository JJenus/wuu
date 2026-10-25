package com.surf.tracker.notification.domain.entity;

import com.surf.tracker.notification.domain.enums.DeliveryStatus;
import com.surf.tracker.notification.domain.enums.ErrorType;
import com.surf.tracker.notification.domain.enums.NotificationChannel;
import com.surf.tracker.shared.util.TimeProvider;
import jakarta.persistence.*;
import java.time.Duration;
import java.time.Instant;

@Entity
@Table(name = "deliveries", indexes = {
    @Index(name = "idx_delivery_id", columnList = "deliveryId", unique = true),
    @Index(name = "idx_delivery_notification_id", columnList = "notification_id"),
    @Index(name = "idx_delivery_status_retry", columnList = "status, nextRetryAt"),
    @Index(name = "idx_delivery_channel", columnList = "channel"),
    @Index(name = "idx_delivery_created", columnList = "createdAt DESC")
})
public class Delivery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String deliveryId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "notification_id", referencedColumnName = "notificationId")
    private NotificationHub notificationHub;

    @Column(name = "device_id")
    private Long deviceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationChannel channel;

    @Column(nullable = false)
    private String recipient;

    @Column(nullable = false)
    private String templateId;

    private String title;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Column(columnDefinition = "TEXT")
    private String templateVariables;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeliveryStatus status = DeliveryStatus.PENDING;

    @Column(nullable = false)
    private int attemptCount = 0;

    @Column(nullable = false)
    private int maxAttempts = 5;

    @Column
    private Instant nextRetryAt;

    @Column(columnDefinition = "TEXT")
    private String lastError;

    @Enumerated(EnumType.STRING)
    private ErrorType lastErrorType;

    @Column
    private Instant sentAt;

    @Column
    private Instant deliveredAt;

    @Column(nullable = false)
    private Instant createdAt = Instant.EPOCH;

    @Column(nullable = false)
    private Instant updatedAt = Instant.EPOCH;

    @PrePersist
    protected void onCreate() {
        if (deliveryId == null) {
            deliveryId = TimeProvider.newId();
        }
        Instant now = TimeProvider.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = TimeProvider.now();
    }

    public void markSent() {
        this.status = DeliveryStatus.SENT;
        this.sentAt = TimeProvider.now();
        this.nextRetryAt = null;
    }

    public void markDelivered() {
        this.status = DeliveryStatus.DELIVERED;
        this.deliveredAt = TimeProvider.now();
    }

    public void markFailed(String error, ErrorType errorType) {
        this.status = DeliveryStatus.FAILED;
        this.lastError = error;
        this.lastErrorType = errorType;
        this.attemptCount++;
    }

    public void markExhausted() {
        this.status = DeliveryStatus.EXHAUSTED;
        this.nextRetryAt = null;
    }

    public void markSkipped() {
        this.status = DeliveryStatus.SKIPPED;
    }

    public boolean canRetry() {
        return status.canRetry() && attemptCount < maxAttempts && lastErrorType == ErrorType.TRANSIENT;
    }

    public void scheduleNextRetry() {
        if (!canRetry()) {
            markExhausted();
            return;
        }
        Duration delay = computeNextRetryDelay(attemptCount);
        this.nextRetryAt = TimeProvider.now().plus(delay);
    }

    public static Duration computeNextRetryDelay(int attemptCount) {
        int base = (int) Math.min(Math.pow(2, attemptCount), 300);
        double jitter = base * 0.2 * (Math.random() * 2 - 1);
        return Duration.ofSeconds((long)(base + jitter));
    }

    public void incrementAttemptCount() {
        this.attemptCount++;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getDeliveryId() { return deliveryId; }
    public void setDeliveryId(String deliveryId) { this.deliveryId = deliveryId; }

    public NotificationHub getNotificationHub() { return notificationHub; }
    public void setNotificationHub(NotificationHub notificationHub) { this.notificationHub = notificationHub; }

    public Long getDeviceId() { return deviceId; }
    public void setDeviceId(Long deviceId) { this.deviceId = deviceId; }

    public NotificationChannel getChannel() { return channel; }
    public void setChannel(NotificationChannel channel) { this.channel = channel; }

    public String getRecipient() { return recipient; }
    public void setRecipient(String recipient) { this.recipient = recipient; }

    public String getTemplateId() { return templateId; }
    public void setTemplateId(String templateId) { this.templateId = templateId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getTemplateVariables() { return templateVariables; }
    public void setTemplateVariables(String templateVariables) { this.templateVariables = templateVariables; }

    public DeliveryStatus getStatus() { return status; }
    public void setStatus(DeliveryStatus status) { this.status = status; }

    public int getAttemptCount() { return attemptCount; }
    public void setAttemptCount(int attemptCount) { this.attemptCount = attemptCount; }

    public int getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }

    public Instant getNextRetryAt() { return nextRetryAt; }
    public void setNextRetryAt(Instant nextRetryAt) { this.nextRetryAt = nextRetryAt; }

    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }

    public ErrorType getLastErrorType() { return lastErrorType; }
    public void setLastErrorType(ErrorType lastErrorType) { this.lastErrorType = lastErrorType; }

    public Instant getSentAt() { return sentAt; }
    public void setSentAt(Instant sentAt) { this.sentAt = sentAt; }

    public Instant getDeliveredAt() { return deliveredAt; }
    public void setDeliveredAt(Instant deliveredAt) { this.deliveredAt = deliveredAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
