package com.surf.tracker.subscription.domain;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.UUID;

final class SubscriptionTestBuilder {
    static final Instant START = Instant.parse("2024-01-31T12:34:56Z");
    static final Instant END = Instant.parse("2024-02-29T12:34:56Z");
    static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private long limit = 10;
    private PlanVersion.BillingModel model = PlanVersion.BillingModel.USAGE_TOKEN;
    private PlanVersion.Cadence cadence;

    static SubscriptionTestBuilder defaultSubscription() { return new SubscriptionTestBuilder(); }
    SubscriptionTestBuilder withLimit(long limit) { this.limit = limit; return this; }
    SubscriptionTestBuilder withPeriodic(PlanVersion.Cadence cadence) {
        this.model = PlanVersion.BillingModel.PERIODIC;
        this.cadence = cadence;
        return this;
    }
    PlanVersion plan() {
        return new PlanVersion(ID, 1, model, cadence, "location_updates", limit,
                new Money(new BigDecimal("2.345"), Currency.getInstance("EUR")));
    }
    Subscription build() { return new Subscription(ID, plan()); }
    Subscription activated() {
        var subscription = build();
        subscription.activate(grant(limit), clock(START));
        return subscription;
    }
    static AdminGrant grant(long units) {
        return new AdminGrant(AdminGrant.Kind.TRIAL, "configured-admin", "explicit test grant", START, END, units);
    }
    static Clock clock(Instant now) { return Clock.fixed(now, ZoneOffset.UTC); }
}
