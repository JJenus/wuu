package com.surf.tracker.subscription.application;

import com.surf.tracker.shared.security.TenantContext;
import com.surf.tracker.subscription.api.dto.*;
import com.surf.tracker.subscription.domain.Money;
import com.surf.tracker.subscription.domain.PlanVersion;
import com.surf.tracker.subscription.infrastructure.persistence.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Authenticated enrollment and immutable plan-catalog persistence.
 *
 * <p>This service deliberately exposes no paid activation, entitlement check, token
 * consumption or renewal. Enrollments are pending configuration records; the
 * reviewed domain aggregate remains the only place that can grant configured trial
 * or complimentary access, and it is not wired to this HTTP surface.</p>
 */
@Service
public class SubscriptionService {

    private static final int MAX_OPERATION_KEY = 200;

    private final PlanVersionRepository planVersions;
    private final SubscriptionRepository enrollments;
    private final Clock clock;

    public SubscriptionService(PlanVersionRepository planVersions, SubscriptionRepository enrollments, Clock clock) {
        this.planVersions = planVersions;
        this.enrollments = enrollments;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<PlanVersionResponse> catalog() {
        return planVersions.findAllByOrderByPlanIdAscVersionAsc().stream()
            .map(entity -> PlanVersionResponse.from(entity.toDomain()))
            .toList();
    }

    @Transactional(readOnly = true)
    public PlanVersionResponse plan(UUID planId, long version) {
        return PlanVersionResponse.from(findPlan(planId, version).toDomain());
    }

    @Transactional
    public PlanVersionResponse createPlan(CreatePlanRequest request) {
        Objects.requireNonNull(request, "request");
        var plan = new PlanVersion(
            parseUuid(request.planId(), "planId"),
            required(request.version(), "version"),
            parseEnum(PlanVersion.BillingModel.class, request.model(), "model"),
            request.cadence() == null ? null : parseEnum(PlanVersion.Cadence.class, request.cadence(), "cadence"),
            required(request.meter(), "meter"),
            required(request.usageLimit(), "usageLimit"),
            storablePrice(required(request.amount(), "amount"),
                Currency.getInstance(required(request.currency(), "currency"))));

        if (planVersions.existsByPlanIdAndVersion(plan.planId(), plan.version())) {
            throw conflict("Plan version already exists");
        }
        try {
            var saved = planVersions.saveAndFlush(
                PlanVersionEntity.from(plan, UUID.randomUUID(), clock.instant()));
            return PlanVersionResponse.from(saved.toDomain());
        } catch (DataIntegrityViolationException race) {
            throw conflict("Plan version already exists");
        }
    }

    /**
     * A repeated operation key returns the original creation receipt, never a second
     * enrollment. The same key with a different plan payload is a conflict.
     */
    @Transactional
    public EnrollmentResult enroll(CreateEnrollmentRequest request) {
        Objects.requireNonNull(request, "request");
        var owner = requireOwner();
        String operationKey = required(request.operationKey(), "operationKey");
        if (operationKey.isBlank() || operationKey.length() > MAX_OPERATION_KEY) {
            throw new IllegalArgumentException("operationKey must be nonblank and at most " + MAX_OPERATION_KEY + " characters");
        }
        UUID planId = parseUuid(request.planId(), "planId");
        long version = required(request.version(), "version");

        var existing = enrollments.findByOrganizationIdAndUserIdAndOperationKey(
            owner.organizationId(), owner.userId(), operationKey);
        if (existing.isPresent()) {
            var entity = existing.get();
            if (!entity.getPlanVersion().getPlanId().equals(planId)
                || entity.getPlanVersion().getVersion() != version) {
                throw conflict("Operation key already used with a different plan payload");
            }
            return new EnrollmentResult(receipt(entity), false);
        }

        var plan = findPlan(planId, version);
        Instant now = clock.instant();
        var entity = SubscriptionEntity.pending(UUID.randomUUID(), owner.organizationId(),
            owner.userId(), operationKey, plan, now);
        // The unique owner/key constraint is the cross-instance winner. A violation
        // rolls this transaction back; the controller replays the committed receipt.
        return new EnrollmentResult(live(enrollments.saveAndFlush(entity)), true);
    }

    @Transactional(readOnly = true)
    public Optional<EnrollmentResponse> replayReceipt(String planId, Long version, String operationKey) {
        var owner = requireOwner();
        if (operationKey == null || operationKey.isBlank() || operationKey.length() > MAX_OPERATION_KEY) {
            return Optional.empty();
        }
        UUID parsedPlan;
        try {
            parsedPlan = UUID.fromString(planId);
        } catch (RuntimeException invalid) {
            return Optional.empty();
        }
        if (version == null) {
            return Optional.empty();
        }
        return enrollments.findByOrganizationIdAndUserIdAndOperationKey(
                owner.organizationId(), owner.userId(), operationKey)
            .filter(entity -> entity.getPlanVersion().getPlanId().equals(parsedPlan)
                && entity.getPlanVersion().getVersion() == version)
            .map(this::receipt);
    }

    @Transactional(readOnly = true)
    public List<EnrollmentResponse> enrollments() {
        var owner = requireOwner();
        return enrollments.findAllByOrganizationIdAndUserIdOrderByCreatedAtAsc(
                owner.organizationId(), owner.userId()).stream()
            .map(this::live)
            .toList();
    }

    @Transactional(readOnly = true)
    public EnrollmentResponse enrollment(UUID id) {
        return live(findEnrollment(id));
    }

    @Transactional
    public EnrollmentResponse cancel(UUID id) {
        var entity = findEnrollment(id);
        entity.cancel(clock.instant());
        return live(entity);
    }

    private SubscriptionEntity findEnrollment(UUID id) {
        var owner = requireOwner();
        return enrollments.findByOrganizationIdAndUserIdAndId(owner.organizationId(), owner.userId(), id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Subscription not found"));
    }

    private PlanVersionEntity findPlan(UUID planId, long version) {
        return planVersions.findByPlanIdAndVersion(planId, version)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Plan version not found"));
    }

    private EnrollmentResponse live(SubscriptionEntity entity) {
        return new EnrollmentResponse(entity.getId(), entity.getPlanVersion().getPlanId(),
            entity.getPlanVersion().getVersion(), entity.getStatus().name(), entity.getRemainingUnits());
    }

    private EnrollmentResponse receipt(SubscriptionEntity entity) {
        return new EnrollmentResponse(entity.getId(), entity.getPlanVersion().getPlanId(),
            entity.getPlanVersion().getVersion(), entity.getReceiptStatus().name(), entity.getReceiptRemainingUnits());
    }

    private Owner requireOwner() {
        Long organizationId = TenantContext.getCurrentOrgId();
        Long userId = TenantContext.getCurrentUserId();
        if (organizationId == null || organizationId <= 0 || userId == null || userId <= 0) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated tenant/user required");
        }
        return new Owner(organizationId, userId);
    }

    private static <T> T required(T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    /** Reject precision the durable numeric(38,18) column would silently round. */
    private static Money storablePrice(BigDecimal amount, Currency currency) {
        var money = new Money(amount, currency);
        var stripped = money.amount().stripTrailingZeros();
        if (stripped.scale() > 18 || stripped.precision() - stripped.scale() > 20) {
            throw new IllegalArgumentException("amount exceeds the storable 38-digit, 18-decimal scale");
        }
        return money;
    }

    private static UUID parseUuid(String value, String field) {
        try {
            return UUID.fromString(required(value, field));
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException(field + " must be a UUID");
        }
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value, String field) {
        try {
            return Enum.valueOf(type, required(value, field));
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException(field + " is invalid");
        }
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    public record EnrollmentResult(EnrollmentResponse response, boolean created) {
    }

    private record Owner(long organizationId, long userId) {
    }
}
