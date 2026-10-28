package com.surf.tracker.notification.domain.enums;

public enum DeliveryEventType {
    CREATED("Delivery created"),
    SENT("Successfully sent"),
    DELIVERED("Confirmed delivery by provider"),
    OPENED("Opened by user"),
    CLICKED("Clicked by user"),
    FAILED("Delivery failed"),
    RETRY_SCHEDULED("Retry scheduled"),
    EXHAUSTED("Max retries exhausted");

    private final String description;

    DeliveryEventType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
