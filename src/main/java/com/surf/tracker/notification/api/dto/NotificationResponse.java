package com.surf.tracker.notification.api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;

public class NotificationResponse {
    @io.swagger.v3.oas.annotations.media.Schema(description = "Stable delivery identifier used for detail and mark-read requests.", example = "delivery-001")
    private String deliveryId;
    @io.swagger.v3.oas.annotations.media.Schema(description = "Associated alert identifier, if present. Marking this delivery read does not acknowledge or resolve that alert.")
    private String alertId;
    @io.swagger.v3.oas.annotations.media.Schema(description = "Delivery channel, not read state.", implementation = com.surf.tracker.notification.domain.enums.NotificationChannel.class)
    private String channel;
    @io.swagger.v3.oas.annotations.media.Schema(description = "Channel-specific delivery destination (for example an email address), not an authorization selector.")
    private String recipient;
    @io.swagger.v3.oas.annotations.media.Schema(description = "Rendered notification title, when available.")
    private String title;
    @io.swagger.v3.oas.annotations.media.Schema(description = "Rendered notification message, when available.")
    private String message;
    @io.swagger.v3.oas.annotations.media.Schema(description = "Provider delivery lifecycle status; OPENED events are tracked separately and do not change this status.")
    private String status;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    @io.swagger.v3.oas.annotations.media.Schema(description = "UTC send time, when recorded; not the time the notification was read.")
    private Instant sentAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    @io.swagger.v3.oas.annotations.media.Schema(description = "UTC provider-delivery time, when recorded; not proof of a user opening the notification.")
    private Instant deliveredAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    @io.swagger.v3.oas.annotations.media.Schema(description = "UTC persistence creation time of this delivery.")
    private Instant createdAt;

    public String getDeliveryId() { return deliveryId; }
    public void setDeliveryId(String deliveryId) { this.deliveryId = deliveryId; }

    public String getAlertId() { return alertId; }
    public void setAlertId(String alertId) { this.alertId = alertId; }

    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }

    public String getRecipient() { return recipient; }
    public void setRecipient(String recipient) { this.recipient = recipient; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getSentAt() { return sentAt; }
    public void setSentAt(Instant sentAt) { this.sentAt = sentAt; }

    public Instant getDeliveredAt() { return deliveredAt; }
    public void setDeliveredAt(Instant deliveredAt) { this.deliveredAt = deliveredAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
