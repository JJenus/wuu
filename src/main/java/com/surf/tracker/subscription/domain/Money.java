package com.surf.tracker.subscription.domain;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Objects;

/** Exact configured price metadata with numeric (not decimal-scale) identity. No rounding or conversion. */
public record Money(BigDecimal amount, Currency currency) {
    public Money {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("Price cannot be negative");
        }
        amount = amount.stripTrailingZeros();
    }
}
