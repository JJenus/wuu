package com.surf.tracker.notification.api.dto;

public record WebhookEmailPayload(
        String deliveryId,
        String status,
        String errorMessage
) {
}
