package com.surf.tracker.subscription.domain;

import java.time.Instant;
import java.util.Objects;

/** Configuration supplied by a future authenticated administrator use case, NOT payment evidence. */
public record AdminGrant(Kind kind, String administratorId, String reason,
                         Instant start, Instant end, long units) {
    public enum Kind { TRIAL, COMPLIMENTARY }

    public AdminGrant {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(administratorId, "administratorId");
        Objects.requireNonNull(reason, "reason");
        new BillingPeriod(start, end);
        if (administratorId.isBlank() || reason.isBlank() || units <= 0) {
            throw new IllegalArgumentException("Explicit administrator, reason and positive finite units required");
        }
    }

    public BillingPeriod period() {
        return new BillingPeriod(start, end);
    }
}
