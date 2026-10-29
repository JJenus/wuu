package com.surf.tracker.subscription.domain;

import org.junit.jupiter.api.Test;
import static com.surf.tracker.subscription.domain.SubscriptionTestBuilder.*;
import static org.assertj.core.api.Assertions.*;

class SubscriptionStateTest {
    @Test void restore_spentGrant_keepsBalanceWithoutRegranting() {
        var original = defaultSubscription().activated();
        original.consume("first", "location_updates", 7, clock(START));

        var restored = Subscription.restore(ID, original.plan(), original.snapshot(clock(START)));

        assertThat(restored.remaining(clock(START))).isEqualTo(3);
        assertThat(restored.consume("second", "location_updates", 3, clock(START)).remainingUnits()).isZero();
        assertThatThrownBy(() -> restored.consume("third", "location_updates", 1, clock(START)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void restore_futureAndExpiredGrant_doesNotInventAccess() {
        var original = defaultSubscription().activated();
        var restored = Subscription.restore(ID, original.plan(), original.snapshot(clock(START.minusSeconds(1))));
        assertThat(restored.remaining(clock(START.minusSeconds(1)))).isZero();
        assertThat(restored.remaining(clock(START))).isEqualTo(10);
        assertThat(restored.snapshot(clock(END)).status()).isEqualTo(Subscription.Status.CANCELED);
    }

    @Test void restore_invalidPersistentState_rejectsBeforeUse() {
        var plan = defaultSubscription().plan();
        for (var state : java.util.List.of(
                new Subscription.State(Subscription.Status.PENDING, grant(10), 0),
                new Subscription.State(Subscription.Status.PENDING, null, 1),
                new Subscription.State(Subscription.Status.TRIALING, null, 0),
                new Subscription.State(Subscription.Status.TRIALING, grant(10), 11),
                new Subscription.State(Subscription.Status.TRIALING, grant(10), -1),
                new Subscription.State(Subscription.Status.ADMIN_GRANTED, grant(10), 10),
                new Subscription.State(Subscription.Status.CANCELED, grant(10), 1))) {
            assertThatThrownBy(() -> Subscription.restore(ID, plan, state)).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
