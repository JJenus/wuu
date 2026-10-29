package com.surf.tracker.subscription.api.dto;

import com.surf.tracker.subscription.domain.PlanVersion;

import java.util.UUID;

public record PlanVersionResponse(UUID planId, long version, String model, String cadence,
                                  String meter, long usageLimit, PriceResponse price) {

    public static PlanVersionResponse from(PlanVersion plan) {
        return new PlanVersionResponse(plan.planId(), plan.version(), plan.model().name(),
            plan.cadence() == null ? null : plan.cadence().name(), plan.meter(), plan.usageLimit(),
            new PriceResponse(plan.price().amount(), plan.price().currency().getCurrencyCode()));
    }
}
