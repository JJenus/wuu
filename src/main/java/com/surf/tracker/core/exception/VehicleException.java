package com.surf.tracker.core.exception;

import com.surf.tracker.shared.exception.BusinessRuleException;

public class VehicleException extends BusinessRuleException {

    public VehicleException(String errorCode, String message) {
        super(errorCode, message);
    }

    public VehicleException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }

    // Specific vehicle exceptions
    public static VehicleException fuelCutNotAllowedWhileMoving(float speed) {
        return new VehicleException(
            "VEHICLE_FUEL_CUT_MOVING",
            String.format("Cannot cut fuel while moving at %.1f km/h. Maximum allowed: 10 km/h", speed)
        );
    }

    public static VehicleException fuelCutAlreadyActive() {
        return new VehicleException(
            "VEHICLE_FUEL_CUT_ACTIVE",
            "Fuel cut is already active for this vehicle"
        );
    }

    public static VehicleException invalidLocationData() {
        return new VehicleException(
            "VEHICLE_INVALID_LOCATION",
            "Invalid location data provided"
        );
    }

    public static VehicleException notFound(String vehicleId) {
        return new VehicleException(
            "VEHICLE_NOT_FOUND",
            String.format("Vehicle with ID '%s' not found", vehicleId)
        );
    }

    public static VehicleException deviceAlreadyAssigned(String deviceId) {
        return new VehicleException(
            "VEHICLE_DEVICE_ASSIGNED",
            String.format("Device with ID '%s' is already assigned to another vehicle", deviceId)
        );
    }
}
