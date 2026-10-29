package com.surf.tracker.subscription.infrastructure.persistence;

import com.surf.tracker.subscription.domain.Subscription;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** Immutable receipt, committed atomically with the locked enrollment balance. */
@Entity
@Table(name = "subscription_usage_receipts")
public class UsageReceiptEntity {
    @Id
    private UUID id;
    @Column(name = "subscription_id", nullable = false, updatable = false)
    private UUID subscriptionId;
    @Column(name = "operation_key", length = 200, nullable = false, updatable = false)
    private String operationKey;
    @Column(name = "meter", length = 100, nullable = false, updatable = false)
    private String meter;
    @Column(name = "units", nullable = false, updatable = false)
    private long units;
    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;
    @Column(name = "remaining_units", nullable = false, updatable = false)
    private long remainingUnits;

    protected UsageReceiptEntity() { }

    public UsageReceiptEntity(Subscription.Consumption receipt) {
        id = UUID.randomUUID();
        subscriptionId = receipt.subscriptionId();
        operationKey = receipt.operationKey();
        meter = receipt.meter();
        units = receipt.units();
        recordedAt = receipt.recordedAt();
        remainingUnits = receipt.remainingUnits();
    }

    public Subscription.Consumption receipt() {
        return new Subscription.Consumption(subscriptionId, operationKey, meter, units, recordedAt, remainingUnits);
    }
}
