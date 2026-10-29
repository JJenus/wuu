package com.surf.tracker.subscription.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.assertj.core.api.Assertions.*;
import static com.surf.tracker.subscription.domain.SubscriptionTestBuilder.*;

class PlanVersionTest {
    @ParameterizedTest
    @EnumSource(PlanVersion.Cadence.class)
    void create_periodic_requires_explicit_cadence(PlanVersion.Cadence cadence) {
        // given / when
        var plan = defaultSubscription().withPeriodic(cadence).plan();
        // then
        assertThat(plan.model()).isEqualTo(PlanVersion.BillingModel.PERIODIC);
        assertThat(plan.cadence()).isEqualTo(cadence);
    }

    @Test
    void create_invalid_configuration_rejects_without_defaults() {
        // given
        var plan = defaultSubscription().plan();
        // when / then
        assertThatThrownBy(() -> defaultSubscription().withPeriodic(null).plan()).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> defaultSubscription().withLimit(0).plan()).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> defaultSubscription().withLimit(-1).plan()).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PlanVersion(ID, 0, plan.model(), null, plan.meter(), 1, plan.price())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PlanVersion(ID, 1, plan.model(), PlanVersion.Cadence.MONTHLY, plan.meter(), 1, plan.price())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PlanVersion(ID, 1, plan.model(), null, " ", 1, plan.price())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void create_new_version_does_not_change_subscribed_snapshot() {
        // given
        var subscription = defaultSubscription().build();
        var original = subscription.plan();
        // when
        var next = new PlanVersion(ID, 2, original.model(), null, original.meter(), 99, original.price());
        // then
        assertThat(next.version()).isEqualTo(2);
        assertThat(subscription.plan()).isSameAs(original);
        assertThat(subscription.plan().usageLimit()).isEqualTo(10);
    }
}
