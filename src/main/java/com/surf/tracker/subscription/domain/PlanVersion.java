package com.surf.tracker.subscription.domain;

import java.util.Objects;
import java.util.UUID;

/** Immutable snapshot pinned by a subscription; new configuration requires a new version. */
public record PlanVersion(UUID planId, long version, BillingModel model, Cadence cadence,
                          String meter, long usageLimit, Money price) {
    public enum BillingModel { USAGE_TOKEN, PERIODIC }
    public enum Cadence { MONTHLY, YEARLY }

    public PlanVersion {
        Objects.requireNonNull(planId, "planId");
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(meter, "meter");
        Objects.requireNonNull(price, "price");
        if (version <= 0 || usageLimit <= 0 || meter.isBlank()) {
            throw new IllegalArgumentException("Positive version, finite positive limit and nonblank meter required");
        }
        if ((model == BillingModel.PERIODIC) != (cadence != null)) {
            throw new IllegalArgumentException("Only periodic plans require a monthly or yearly cadence");
        }
    }
}
