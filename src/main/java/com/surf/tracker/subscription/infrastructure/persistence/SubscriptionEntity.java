package com.surf.tracker.subscription.infrastructure.persistence;

import jakarta.persistence.*;
import com.surf.tracker.subscription.domain.AdminGrant;
import com.surf.tracker.subscription.domain.Subscription;

import java.time.Instant;
import java.util.UUID;

/**
 * Durable enrollment and request-idempotency receipt. The receipt columns preserve the
 * original POST response for replay even after the live lifecycle state changes.
 */
@Entity
@Table(name = "subscription_enrollments",
    uniqueConstraints = @UniqueConstraint(name = "uk_subscription_enrollment_key",
        columnNames = {"organization_id", "user_id", "operation_key"}))
public class SubscriptionEntity {

    public enum Status { PENDING, TRIALING, ADMIN_GRANTED, CANCELED }

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private Long organizationId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "operation_key", length = 200, nullable = false, updatable = false)
    private String operationKey;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_version_id", nullable = false, updatable = false)
    private PlanVersionEntity planVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 32, nullable = false)
    private Status status;

    @Column(name = "remaining_units", nullable = false)
    private long remainingUnits;

    @Enumerated(EnumType.STRING)
    @Column(name = "receipt_status", length = 32, nullable = false, updatable = false)
    private Status receiptStatus;

    @Column(name = "receipt_remaining_units", nullable = false, updatable = false)
    private long receiptRemainingUnits;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "grant_operation_key", length = 200)
    private String grantOperationKey;
    @Enumerated(EnumType.STRING)
    @Column(name = "grant_kind", length = 32)
    private AdminGrant.Kind grantKind;
    @Column(name = "grant_administrator_id", length = 40)
    private String grantAdministratorId;
    @Column(name = "grant_reason", length = 500)
    private String grantReason;
    @Column(name = "grant_start")
    private Instant grantStart;
    @Column(name = "grant_end")
    private Instant grantEnd;
    @Column(name = "grant_units")
    private Long grantUnits;

    public AdminGrant configuredGrant() {
        return grantOperationKey == null ? null : new AdminGrant(grantKind, grantAdministratorId,
            grantReason, grantStart, grantEnd, grantUnits);
    }

    public String getGrantOperationKey() { return grantOperationKey; }

    public Subscription aggregate() {
        return Subscription.restore(id, planVersion.toDomain(),
            new Subscription.State(Subscription.Status.valueOf(status.name()), configuredGrant(), remainingUnits));
    }

    public void apply(Subscription.State state, Instant now) {
        status = Status.valueOf(state.status().name());
        remainingUnits = state.remainingUnits();
        updatedAt = now;
    }

    public void recordGrant(String key, AdminGrant grant) {
        if (grantOperationKey != null) throw new IllegalStateException("Grant is immutable");
        grantOperationKey = key;
        grantKind = grant.kind();
        grantAdministratorId = grant.administratorId();
        grantReason = grant.reason();
        grantStart = grant.start();
        grantEnd = grant.end();
        grantUnits = grant.units();
    }

    protected SubscriptionEntity() {
    }

    public static SubscriptionEntity pending(UUID id, Long organizationId, Long userId,
                                             String operationKey, PlanVersionEntity plan,
                                             Instant now) {
        var entity = new SubscriptionEntity();
        entity.id = id;
        entity.organizationId = organizationId;
        entity.userId = userId;
        entity.operationKey = operationKey;
        entity.planVersion = plan;
        entity.status = Status.PENDING;
        entity.remainingUnits = 0;
        entity.receiptStatus = Status.PENDING;
        entity.receiptRemainingUnits = 0;
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    /** Terminal, idempotent, and deliberately does not touch the original request receipt. */
    public void cancel(Instant now) {
        if (status != Status.CANCELED) {
            status = Status.CANCELED;
            remainingUnits = 0;
            updatedAt = now;
        }
    }

    public UUID getId() {
        return id;
    }

    public Long getOrganizationId() {
        return organizationId;
    }

    public Long getUserId() {
        return userId;
    }

    public String getOperationKey() {
        return operationKey;
    }

    public PlanVersionEntity getPlanVersion() {
        return planVersion;
    }

    public Status getStatus() {
        return status;
    }

    public long getRemainingUnits() {
        return remainingUnits;
    }

    public Status getReceiptStatus() {
        return receiptStatus;
    }

    public long getReceiptRemainingUnits() {
        return receiptRemainingUnits;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
