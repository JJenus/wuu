package com.surf.tracker.notification.domain.enums;

public enum DeliveryStatus {
    PENDING("Pending delivery"),
    SENDING("Currently sending"),
    SENT("Successfully sent"),
    DELIVERED("Confirmed delivery"),
    FAILED("Delivery failed"),
    EXHAUSTED("Exhausted after max attempts"),
    SKIPPED("Channel disabled, skipped");

    private final String description;

    DeliveryStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public boolean isFinal() {
        return this == DELIVERED || this == EXHAUSTED || this == SKIPPED || this == SENT;
    }

    public boolean canRetry() {
        return this == FAILED;
    }
}
