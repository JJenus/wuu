package com.surf.tracker.subscription.api.dto;

import java.math.BigDecimal;

public record CreatePlanRequest(String planId, Long version, String model, String cadence,
                                String meter, Long usageLimit, BigDecimal amount, String currency) {
}
