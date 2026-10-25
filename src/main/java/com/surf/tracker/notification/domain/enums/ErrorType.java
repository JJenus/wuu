package com.surf.tracker.notification.domain.enums;

public enum ErrorType {
    TRANSIENT("Retryable error - network, rate limit, etc."),
    PERMANENT("Non-retryable error - invalid address, unsubscribed, etc.");

    private final String description;

    ErrorType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
