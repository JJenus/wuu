package com.surf.tracker.userauth.domain.enums;

public enum FailureReason {
    INVALID_CREDENTIALS,
    USER_NOT_FOUND,
    USER_DISABLED,
    USER_LOCKED,
    ACCOUNT_LOCKED,
    INVALID_TOKEN,
    EXPIRED_TOKEN,
    REUSED_TOKEN,
    RATE_LIMITED
}
