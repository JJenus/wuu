package com.surf.tracker.alerting.exception;

import com.surf.tracker.shared.exception.BusinessRuleException;

public class AlertException extends BusinessRuleException {

    public AlertException(String errorCode, String message) {
        super(errorCode, message);
    }

    public AlertException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }

    public static AlertException ruleNotFound(String ruleKey) {
        return new AlertException(
            "ALERT_RULE_NOT_FOUND",
            String.format("Alert rule with key '%s' not found", ruleKey)
        );
    }

    public static AlertException ruleAlreadyExists(String ruleKey) {
        return new AlertException(
            "ALERT_RULE_EXISTS",
            String.format("Alert rule with key '%s' already exists", ruleKey)
        );
    }

    public static AlertException invalidConfiguration(String ruleKey, String detail) {
        return new AlertException(
            "ALERT_INVALID_CONFIG",
            String.format("Invalid configuration for rule '%s': %s", ruleKey, detail)
        );
    }

    public static AlertException geofenceNotFound(String geofenceId) {
        return new AlertException(
            "ALERT_GEOFENCE_NOT_FOUND",
            String.format("Geofence with ID '%s' not found", geofenceId)
        );
    }

    public static AlertException evaluationError(String ruleKey, String detail) {
        return new AlertException(
            "ALERT_EVALUATION_ERROR",
            String.format("Error evaluating rule '%s': %s", ruleKey, detail)
        );
    }

    public static AlertException alertNotFound(Long alertId) {
        return new AlertException(
                "ALERT_NOT_FOUND_ERROR",
                String.format("Error alert '%s' not found", alertId)
        );
    }
}
