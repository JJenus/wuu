package com.surf.tracker.subscription.domain;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Objects;

/** Half-open UTC window. Every boundary is derived from the original anchor, never its predecessor. */
public record BillingPeriod(Instant start, Instant end) {
    public BillingPeriod {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException("Period end must follow start");
        }
    }

    public static BillingPeriod anchored(Instant anchor, PlanVersion.Cadence cadence, long index) {
        Objects.requireNonNull(anchor, "anchor");
        Objects.requireNonNull(cadence, "cadence");
        if (index < 0) {
            throw new IllegalArgumentException("Period index cannot be negative");
        }
        var utc = anchor.atZone(ZoneOffset.UTC);
        return new BillingPeriod(boundary(utc, cadence, index),
                boundary(utc, cadence, Math.addExact(index, 1)));
    }

    private static Instant boundary(ZonedDateTime anchor, PlanVersion.Cadence cadence, long index) {
        return switch (cadence) {
            case MONTHLY -> anchor.plusMonths(index).toInstant();
            case YEARLY -> anchor.plusYears(index).toInstant();
        };
    }

    public boolean contains(Instant instant) {
        Objects.requireNonNull(instant, "instant");
        return !instant.isBefore(start) && instant.isBefore(end);
    }
}
