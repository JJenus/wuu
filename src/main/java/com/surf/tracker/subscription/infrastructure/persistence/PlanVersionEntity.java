package com.surf.tracker.subscription.infrastructure.persistence;

import com.surf.tracker.subscription.domain.Money;
import com.surf.tracker.subscription.domain.PlanVersion;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.UUID;

/** Durable, immutable plan-version snapshot. Uniqueness is enforced by the database. */
@Entity
@Table(name = "subscription_plan_versions",
    uniqueConstraints = @UniqueConstraint(name = "uk_subscription_plan_version", columnNames = {"plan_id", "version"}))
public class PlanVersionEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "plan_id", nullable = false, updatable = false)
    private UUID planId;

    @Column(name = "version", nullable = false, updatable = false)
    private long version;

    @Enumerated(EnumType.STRING)
    @Column(name = "model", length = 32, nullable = false, updatable = false)
    private PlanVersion.BillingModel model;

    @Enumerated(EnumType.STRING)
    @Column(name = "cadence", length = 16, updatable = false)
    private PlanVersion.Cadence cadence;

    @Column(name = "meter", length = 100, nullable = false, updatable = false)
    private String meter;

    @Column(name = "usage_limit", nullable = false, updatable = false)
    private long usageLimit;

    @Column(name = "amount", precision = 38, scale = 18, nullable = false, updatable = false)
    private BigDecimal amount;

    @Column(name = "currency", length = 3, nullable = false, updatable = false)
    private String currency;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PlanVersionEntity() {
    }

    public static PlanVersionEntity from(PlanVersion plan, UUID id, Instant createdAt) {
        var entity = new PlanVersionEntity();
        entity.id = id;
        entity.planId = plan.planId();
        entity.version = plan.version();
        entity.model = plan.model();
        entity.cadence = plan.cadence();
        entity.meter = plan.meter();
        entity.usageLimit = plan.usageLimit();
        entity.amount = plan.price().amount();
        entity.currency = plan.price().currency().getCurrencyCode();
        entity.createdAt = createdAt;
        return entity;
    }

    public PlanVersion toDomain() {
        return new PlanVersion(planId, version, model, cadence, meter, usageLimit,
            new Money(amount, Currency.getInstance(currency)));
    }

    public UUID getId() {
        return id;
    }

    public UUID getPlanId() {
        return planId;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
