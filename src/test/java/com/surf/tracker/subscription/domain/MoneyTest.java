package com.surf.tracker.subscription.domain;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.Currency;
import static org.assertj.core.api.Assertions.*;

class MoneyTest {
    @Test
    void create_equivalent_decimal_scales_have_equal_price_identity() {
        // given
        var currency = Currency.getInstance("EUR");
        // when
        var first = new Money(new BigDecimal("1.0"), currency);
        var second = new Money(new BigDecimal("1.00"), currency);
        // then
        assertThat(first).isEqualTo(second);
        assertThat(first.hashCode()).isEqualTo(second.hashCode());
    }

    @Test
    void create_exact_amount_preserves_precision_and_currency() {
        // given
        var amount = new BigDecimal("123.456789");
        var currency = Currency.getInstance("EUR");
        // when
        var money = new Money(amount, currency);
        // then
        assertThat(money.amount()).isEqualTo(amount);
        assertThat(money.currency()).isEqualTo(currency);
    }

    @Test
    void create_negative_or_missing_values_rejects_configuration() {
        // given
        var currency = Currency.getInstance("EUR");
        // when / then
        assertThatThrownBy(() -> new Money(BigDecimal.ONE.negate(), currency)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Money(null, currency)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Money(BigDecimal.ZERO, null)).isInstanceOf(NullPointerException.class);
        assertThat(new Money(BigDecimal.ZERO, currency).amount()).isZero();
    }
}
