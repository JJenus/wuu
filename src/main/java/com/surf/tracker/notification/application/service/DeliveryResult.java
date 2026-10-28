package com.surf.tracker.notification.application.service;

import com.surf.tracker.notification.domain.enums.ErrorType;

public record DeliveryResult(boolean ok, String error, ErrorType errorType) {

    public static DeliveryResult success() {
        return new DeliveryResult(true, null, null);
    }

    public static DeliveryResult failure(String error, ErrorType errorType) {
        return new DeliveryResult(false, error, errorType);
    }
}