package com.surf.tracker.shared.exception;

public class InfrastructureException extends DomainException {
    public InfrastructureException(String errorCode, String message) {
        super(errorCode, message);
    }

    public InfrastructureException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
