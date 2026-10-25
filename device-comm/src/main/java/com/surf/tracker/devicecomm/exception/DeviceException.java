package com.surf.tracker.devicecomm.exception;

import com.surf.tracker.shared.exception.BusinessRuleException;

public class DeviceException extends BusinessRuleException {

    public DeviceException(String errorCode, String message) {
        super(errorCode, message);
    }

    public DeviceException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }

    public static DeviceException notFound(String deviceId) {
        return new DeviceException(
            "DEVICE_NOT_FOUND",
            String.format("Device with ID '%s' not found", deviceId)
        );
    }

    public static DeviceException offline(String deviceId) {
        return new DeviceException(
            "DEVICE_OFFLINE",
            String.format("Device with ID '%s' is offline", deviceId)
        );
    }

    public static DeviceException communicationError(String deviceId) {
        return new DeviceException(
            "DEVICE_COMMUNICATION_ERROR",
            String.format("Communication error with device '%s'", deviceId)
        );
    }

    public static DeviceException commandTimeout(String deviceId, String commandType) {
        return new DeviceException(
            "DEVICE_COMMAND_TIMEOUT",
            String.format("Command '%s' timeout for device '%s'", commandType, deviceId)
        );
    }

    public static DeviceException invalidResponse(String deviceId, String expected, String received) {
        return new DeviceException(
            "DEVICE_INVALID_RESPONSE",
            String.format("Invalid response from device '%s'. Expected: %s, Received: %s",
                         deviceId, expected, received)
        );
    }
}
