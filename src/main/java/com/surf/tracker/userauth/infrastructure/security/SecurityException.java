package com.surf.tracker.userauth.infrastructure.security;

import com.surf.tracker.shared.exception.DomainException;

public class SecurityException extends DomainException {
    public SecurityException(String errorCode, String message) {
        super(errorCode, message);
    }
}
