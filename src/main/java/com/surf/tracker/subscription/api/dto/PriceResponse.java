package com.surf.tracker.subscription.api.dto;

import java.math.BigDecimal;

public record PriceResponse(BigDecimal amount, String currency) {
}
