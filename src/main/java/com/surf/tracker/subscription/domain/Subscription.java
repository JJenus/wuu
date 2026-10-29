package com.surf.tracker.subscription.domain;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Process-local domain aggregate, not a repository or production entitlement service.
 * The monitor makes grant, debit, replay and cancellation atomic only for THIS instance.
 */
public final class Subscription {
    public enum Status { PENDING, TRIALING, ADMIN_GRANTED, CANCELED }

    public record Consumption(UUID subscriptionId, String operationKey, String meter, long units,
                              Instant recordedAt, long remainingUnits) { }

    /** Trusted persistence state only; successful-operation receipts live in the durable adapter. */
    public record State(Status status, AdminGrant grant, long remainingUnits) { }

    public static Subscription restore(UUID id, PlanVersion plan, State state) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(state.status(), "status");
        var result = new Subscription(id, plan);
        var grant = state.grant();
        if (state.remainingUnits() < 0
                || (state.status() == Status.PENDING && (grant != null || state.remainingUnits() != 0))
                || (state.status() == Status.CANCELED && state.remainingUnits() != 0)
                || (grant != null && (grant.units() > plan.usageLimit() || state.remainingUnits() > grant.units()))) {
            throw new IllegalArgumentException("Invalid persistent subscription balance/state");
        }
        if (state.status() == Status.TRIALING || state.status() == Status.ADMIN_GRANTED) {
            if (grant == null || (state.status() == Status.TRIALING) != (grant.kind() == AdminGrant.Kind.TRIAL)) {
                throw new IllegalArgumentException("Active lifecycle requires a matching explicit grant");
            }
        }
        result.status = state.status();
        result.grant = grant;
        result.remainingUnits = state.remainingUnits();
        return result;
    }

    /** Raw balance is retained for future-start grants; remaining(clock) is the spendable projection. */
    public synchronized State snapshot(Clock clock) {
        expire(Objects.requireNonNull(clock, "clock").instant());
        return new State(status, grant, remainingUnits);
    }

    public static final class IdempotencyConflict extends IllegalStateException {
        public IdempotencyConflict() {
            super("Operation key already used with a different consumption payload");
        }
    }

    private final UUID id;
    private final PlanVersion plan;
    private final Map<String, Consumption> ledger = new LinkedHashMap<>();
    private Status status = Status.PENDING;
    private AdminGrant grant;
    private long remainingUnits;

    public Subscription(UUID id, PlanVersion plan) {
        this.id = Objects.requireNonNull(id, "id");
        this.plan = Objects.requireNonNull(plan, "plan");
    }

    public PlanVersion plan() {
        return plan;
    }

    /** One non-paid configured grant only. Renewal/top-up/payment transitions are deliberately absent. */
    public synchronized void activate(AdminGrant configuredGrant, Clock clock) {
        var now = Objects.requireNonNull(clock, "clock").instant();
        Objects.requireNonNull(configuredGrant, "configuredGrant");
        expire(now);
        if (status != Status.PENDING) {
            throw new IllegalStateException("Only pending subscriptions can receive an activation grant");
        }
        if (configuredGrant.units() > plan.usageLimit() || !now.isBefore(configuredGrant.end())) {
            throw new IllegalArgumentException("Grant exceeds plan limit or has already ended");
        }
        grant = configuredGrant;
        remainingUnits = configuredGrant.units();
        status = configuredGrant.kind() == AdminGrant.Kind.TRIAL ? Status.TRIALING : Status.ADMIN_GRANTED;
    }

    /** Successful-operation keys are scoped to this subscription and retained for its lifetime. */
    public synchronized Consumption consume(String operationKey, String meter, long units, Clock clock) {
        Objects.requireNonNull(operationKey, "operationKey");
        Objects.requireNonNull(meter, "meter");
        Objects.requireNonNull(clock, "clock");
        if (operationKey.isBlank()) {
            throw new IllegalArgumentException("Operation key cannot be blank");
        }
        var existing = ledger.get(operationKey);
        if (existing != null) {
            if (!existing.meter().equals(meter) || existing.units() != units) {
                throw new IdempotencyConflict();
            }
            return existing;
        }
        if (units <= 0 || !plan.meter().equals(meter)) {
            throw new IllegalArgumentException("Positive integer units and the plan's exact meter required");
        }
        var now = clock.instant();
        expire(now);
        if (status == Status.PENDING || status == Status.CANCELED || !grant.period().contains(now)) {
            throw new IllegalStateException("No current activation grant");
        }
        if (units > remainingUnits) {
            throw new IllegalArgumentException("Insufficient granted units");
        }
        // Subtract after the bound check: no cumulative addition can wrap at Long.MAX_VALUE.
        var receipt = new Consumption(id, operationKey, meter, units, now, remainingUnits - units);
        ledger.put(operationKey, receipt);
        remainingUnits = receipt.remainingUnits();
        return receipt;
    }

    public synchronized void cancel() {
        status = Status.CANCELED;
        remainingUnits = 0;
    }

    /** Lifecycle label only: a future-start grant may be labeled active before it grants access.
     * Authorize consumption through consume(), never from this status alone.
     */
    public synchronized Status status(Clock clock) {
        expire(Objects.requireNonNull(clock, "clock").instant());
        return status;
    }

    public synchronized long remaining(Clock clock) {
        var now = Objects.requireNonNull(clock, "clock").instant();
        expire(now);
        return grant != null && status != Status.CANCELED && grant.period().contains(now) ? remainingUnits : 0;
    }

    public synchronized Optional<AdminGrant> grant() {
        return Optional.ofNullable(grant);
    }

    public synchronized List<Consumption> ledger() {
        return List.copyOf(ledger.values());
    }

    private void expire(Instant now) {
        if (grant != null && !now.isBefore(grant.end())) {
            cancel();
        }
    }
}
