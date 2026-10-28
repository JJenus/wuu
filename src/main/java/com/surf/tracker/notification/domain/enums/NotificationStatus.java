package com.surf.tracker.notification.domain.enums;

public enum NotificationStatus {
    CREATED("Notification hub created"),
    PROCESSING("Processing deliveries"),
    COMPLETED("All deliveries completed"),
    PARTIAL_FAILURE("Some deliveries failed"),
    FAILED("All deliveries failed");

    private final String description;

    NotificationStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public boolean isFinal() {
        return this == COMPLETED || this == FAILED || this == PARTIAL_FAILURE;
    }
}
