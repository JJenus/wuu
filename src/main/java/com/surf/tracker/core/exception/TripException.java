package com.surf.tracker.core.exception;

import com.surf.tracker.shared.exception.BusinessRuleException;

public class TripException extends BusinessRuleException {

    public TripException(String errorCode, String message) {
        super(errorCode, message);
    }

    public TripException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }

    public static TripException alreadyActive(String vehicleId) {
        return new TripException(
            "TRIP_ALREADY_ACTIVE",
            String.format("Vehicle '%s' already has an active trip", vehicleId)
        );
    }

    public static TripException notActive(String vehicleId) {
        return new TripException(
            "TRIP_NOT_ACTIVE",
            String.format("Vehicle '%s' does not have an active trip", vehicleId)
        );
    }

    public static TripException notFound(String tripId) {
        return new TripException(
            "TRIP_NOT_FOUND",
            String.format("Trip with ID '%s' not found", tripId)
        );
    }

    public static TripException invalidDuration() {
        return new TripException(
            "TRIP_INVALID_DURATION",
            "Trip duration cannot be negative"
        );
    }
}
