package com.surf.tracker.shared.exception;

public class BusinessRuleException extends DomainException {
    public BusinessRuleException(String errorCode, String message) {
        super(errorCode, message);
    }

    public BusinessRuleException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
