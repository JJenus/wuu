package com.surf.tracker.userauth.infrastructure.security;

import com.surf.tracker.shared.exception.DomainException;

public class AuthException extends DomainException {
    public AuthException(String errorCode, String message) {
        super(errorCode, message);
    }
}
