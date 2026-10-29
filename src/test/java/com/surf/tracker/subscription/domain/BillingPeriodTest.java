package com.surf.tracker.subscription.domain;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;
import static com.surf.tracker.subscription.domain.PlanVersion.Cadence.*;
import static com.surf.tracker.subscription.domain.SubscriptionTestBuilder.*;

class BillingPeriodTest {
    @Test
    void anchored_month_end_uses_original_anchor_without_drift() {
        // given / when
        var february = BillingPeriod.anchored(START, MONTHLY, 1);
        var march = BillingPeriod.anchored(START, MONTHLY, 2);
        // then
        assertThat(february.start()).isEqualTo(END);
        assertThat(february.end()).isEqualTo(Instant.parse("2024-03-31T12:34:56Z"));
        assertThat(march.start()).isEqualTo(february.end());
        assertThat(march.end()).isEqualTo(Instant.parse("2024-04-30T12:34:56Z"));
    }

    @Test
    void anchored_leap_day_restores_february_29_in_next_leap_year() {
        // given
        var anchor = END;
        // when
        var ordinary = BillingPeriod.anchored(anchor, YEARLY, 1);
        var leap = BillingPeriod.anchored(anchor, YEARLY, 4);
        // then
        assertThat(ordinary.start()).isEqualTo(Instant.parse("2025-02-28T12:34:56Z"));
        assertThat(leap.start()).isEqualTo(Instant.parse("2028-02-29T12:34:56Z"));
    }

    @Test
    void anchored_nonleap_february_preserves_utc_time_and_march_anchor() {
        // given
        var anchor = Instant.parse("2025-01-31T23:59:59.123456789Z");
        // when
        var period = BillingPeriod.anchored(anchor, MONTHLY, 1);
        // then
        assertThat(period.start()).isEqualTo(Instant.parse("2025-02-28T23:59:59.123456789Z"));
        assertThat(period.end()).isEqualTo(Instant.parse("2025-03-31T23:59:59.123456789Z"));
    }

    @Test
    void contains_half_open_window_excludes_end() {
        // given
        var period = new BillingPeriod(START, END);
        // when / then
        assertThat(period.contains(START)).isTrue();
        assertThat(period.contains(END.minusNanos(1))).isTrue();
        assertThat(period.contains(END)).isFalse();
        assertThat(period.contains(START.minusNanos(1))).isFalse();
    }

    @Test
    void anchored_invalid_or_overflowing_index_rejects() {
        // given / when / then
        assertThatThrownBy(() -> BillingPeriod.anchored(START, MONTHLY, -1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BillingPeriod.anchored(START, YEARLY, Long.MAX_VALUE)).isInstanceOf(java.time.DateTimeException.class);
        assertThatThrownBy(() -> BillingPeriod.anchored(START, MONTHLY, Long.MAX_VALUE)).isInstanceOf(java.time.DateTimeException.class);
        assertThatThrownBy(() -> BillingPeriod.anchored(Instant.MAX, MONTHLY, 0)).isInstanceOf(java.time.DateTimeException.class);
        assertThatThrownBy(() -> BillingPeriod.anchored(Instant.MIN, YEARLY, 0)).isInstanceOf(java.time.DateTimeException.class);
        assertThatThrownBy(() -> new BillingPeriod(END, START)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BillingPeriod(START, START)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anchored_index_zero_preserves_anchor_across_calendar_year() {
        // given
        var anchor = Instant.parse("2025-12-31T23:59:59Z");
        // when
        var first = BillingPeriod.anchored(anchor, MONTHLY, 0);
        // then
        assertThat(first.start()).isEqualTo(anchor);
        assertThat(first.end()).isEqualTo(Instant.parse("2026-01-31T23:59:59Z"));
    }
}
