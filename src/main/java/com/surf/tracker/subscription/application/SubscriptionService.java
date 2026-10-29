package com.surf.tracker.subscription.application;

import com.surf.tracker.shared.security.TenantContext;
import com.surf.tracker.subscription.api.dto.*;
import com.surf.tracker.subscription.domain.Money;
import com.surf.tracker.subscription.domain.PlanVersion;
import com.surf.tracker.subscription.domain.AdminGrant;
import com.surf.tracker.subscription.domain.Subscription;
import org.springframework.security.access.prepost.PreAuthorize;
import com.surf.tracker.subscription.infrastructure.persistence.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Currency;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Authenticated enrollment and immutable plan-catalog persistence.
 *
 * <p>Configured trial/complimentary grants and administrator-recorded usage reuse
 * the domain aggregate under a database row lock. Enrollment alone grants nothing.
 * Paid activation, business-feature enforcement and recurring renewal remain absent.</p>
 */
@Service
public class SubscriptionService {

    private static final int MAX_OPERATION_KEY = 200;

    private final PlanVersionRepository planVersions;
    private final SubscriptionRepository enrollments;
    private final Clock clock;
    private final UsageReceiptRepository usageReceipts;

    public SubscriptionService(PlanVersionRepository planVersions, SubscriptionRepository enrollments, Clock clock,
                               UsageReceiptRepository usageReceipts) {
        this.planVersions = planVersions;
        this.enrollments = enrollments;
        this.clock = clock;
        this.usageReceipts = usageReceipts;
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
            validMeter(request.meter()),
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
        var owner = requireOwner();
        var entity = enrollments.lockForOwner(owner.organizationId(), owner.userId(), id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Subscription not found"));
        var aggregate = entity.aggregate();
        aggregate.cancel();
        entity.apply(aggregate.snapshot(clock), clock.instant());
        return live(entity);
    }

    @Transactional
    @PreAuthorize("hasAuthority('system.admin')")
    public AdminGrant grant(UUID id, GrantRequest request) {
        var owner = requireOwner();
        String key = validOperationKey(request.operationKey());
        String reason = required(request.reason(), "reason");
        if (reason.isBlank() || reason.length() > 500) {
            throw new IllegalArgumentException("reason must be nonblank and at most 500 characters");
        }
        var grant = new AdminGrant(parseEnum(AdminGrant.Kind.class, request.kind(), "kind"),
            Long.toString(owner.userId()), reason, durableInstant(request.start()), durableInstant(request.end()),
            required(request.units(), "units"));
        var entity = lockedForAdministrator(owner, id);
        if (entity.getGrantOperationKey() != null) {
            if (!entity.getGrantOperationKey().equals(key) || !entity.configuredGrant().equals(grant)) {
                throw conflict("Subscription already has a different grant");
            }
            return entity.configuredGrant();
        }
        var aggregate = entity.aggregate();
        try {
            aggregate.activate(grant, clock);
        } catch (IllegalStateException rejected) {
            throw conflict("Only pending subscriptions can receive a grant");
        }
        entity.recordGrant(key, grant);
        entity.apply(aggregate.snapshot(clock), clock.instant());
        return grant;
    }

    /** Trusted administrator recording only; this is not customer-supplied product metering. */
    @Transactional
    @PreAuthorize("hasAuthority('system.admin')")
    public Subscription.Consumption consume(UUID id, UsageRequest request) {
        var owner = requireOwner();
        String key = validOperationKey(request.operationKey());
        String meter = validMeter(request.meter());
        long units = required(request.units(), "units");
        var entity = lockedForAdministrator(owner, id);
        var existing = usageReceipts.findBySubscriptionIdAndOperationKey(id, key);
        if (existing.isPresent()) {
            var receipt = existing.get().receipt();
            if (!receipt.meter().equals(meter) || receipt.units() != units) {
                throw conflict("Operation key already used with different usage");
            }
            return receipt;
        }
        var aggregate = entity.aggregate();
        // Database timestamp precision is microseconds; persist and return the same receipt.
        var operationClock = Clock.fixed(clock.instant().truncatedTo(ChronoUnit.MICROS), ZoneOffset.UTC);
        Subscription.Consumption receipt;
        try {
            receipt = aggregate.consume(key, meter, units, operationClock);
        } catch (IllegalStateException rejected) {
            throw conflict("No current activation grant");
        }
        entity.apply(aggregate.snapshot(operationClock), operationClock.instant());
        usageReceipts.saveAndFlush(new UsageReceiptEntity(receipt));
        return receipt;
    }

    private SubscriptionEntity lockedForAdministrator(Owner owner, UUID id) {
        return enrollments.lockForAdministrator(owner.organizationId(), id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Subscription not found"));
    }

    private static Instant durableInstant(Instant value) {
        required(value, "grant time");
        if (value.getNano() % 1000 != 0 || value.isBefore(Instant.parse("0001-01-01T00:00:00Z"))
                || value.isAfter(Instant.parse("9999-12-31T23:59:59.999999Z"))) {
            throw new IllegalArgumentException("Grant times require microsecond precision and years 1 through 9999");
        }
        return value;
    }

    private static String validOperationKey(String value) {
        required(value, "operationKey");
        if (value.isBlank() || value.length() > MAX_OPERATION_KEY) {
            throw new IllegalArgumentException("operationKey must be nonblank and at most 200 characters");
        }
        return value;
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
        var aggregate = entity.aggregate();
        var readClock = Clock.fixed(clock.instant(), ZoneOffset.UTC);
        return new EnrollmentResponse(entity.getId(), entity.getPlanVersion().getPlanId(),
            entity.getPlanVersion().getVersion(), aggregate.status(readClock).name(), aggregate.remaining(readClock));
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

    private static String validMeter(String meter) {
        required(meter, "meter");
        if (meter.isBlank() || meter.length() > 100) {
            throw new IllegalArgumentException("meter must be nonblank and at most 100 characters");
        }
        return meter;
    }

    /** Reject precision the durable numeric(38,18) column would silently round. */
    private static Money storablePrice(BigDecimal amount, Currency currency) {
        var money = new Money(amount, currency);
        var stripped = money.amount().stripTrailingZeros();
        if (stripped.scale() > 18 || (long) stripped.precision() - stripped.scale() > 20) {
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
