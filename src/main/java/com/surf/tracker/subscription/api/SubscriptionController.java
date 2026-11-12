package com.surf.tracker.subscription.api;

import com.surf.tracker.main.api.ApiV1;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.surf.tracker.subscription.api.dto.*;
import com.surf.tracker.subscription.application.SubscriptionService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@ApiV1
@RequestMapping("/subscriptions")
@Tag(name = "Subscriptions", description = "Versioned plan catalog and owner-scoped enrollments. Enrollment does not prove payment or activate paid access.")
public class SubscriptionController {

    private final SubscriptionService subscriptions;

    public SubscriptionController(SubscriptionService subscriptions) {
        this.subscriptions = subscriptions;
    }

    @PostMapping("/catalog")
    @Operation(operationId = "subscriptionsPlanCreate", summary = "Create a plan version", description = "Requires system.admin. Persists the catalog definition through the subscription service. A plan's business version is independent of the HTTP API major version.")
    @ApiResponse(responseCode = "201", description = "Plan version created")
    public ResponseEntity<PlanVersionResponse> createPlan(@RequestBody CreatePlanRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(subscriptions.createPlan(request));
    }

    @GetMapping("/catalog")
    @Operation(operationId = "subscriptionsCatalog", summary = "Read the plan catalog", description = "Requires an authenticated bearer session. Returns an unpaginated array of plan versions from the catalog; reading a plan does not enroll the caller or grant entitlements.")
    public ResponseEntity<List<PlanVersionResponse>> catalog() {
        return ResponseEntity.ok(subscriptions.catalog());
    }

    @GetMapping("/catalog/{planId}/{version}")
    @Operation(operationId = "subscriptionsPlanGet", summary = "Read a specific plan version", description = "Requires an authenticated bearer session. planId is a UUID and version is the plan's numeric business revision, not the API version. A malformed UUID deliberately returns 404.")
    @ApiResponse(responseCode = "404", description = "Plan version not found or malformed planId")
    @ApiResponse(responseCode = "200", description = "Requested business plan version")
    public ResponseEntity<PlanVersionResponse> plan(@PathVariable String planId, @PathVariable long version) {
        UUID parsed;
        try {
            parsed = UUID.fromString(planId);
        } catch (IllegalArgumentException invalid) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Plan version not found");
        }
        return ResponseEntity.ok(subscriptions.plan(parsed, version));
    }

    @PostMapping
    @Operation(operationId = "subscriptionsEnroll", summary = "Create or replay a pending enrollment", description = "Requires an authenticated bearer session. Accepts plan_id, plan version and operation_key. Owner and tenant come from the session. A newly created pending enrollment returns 201; replay of the same operation returns 200. An unreconciled uniqueness race returns 409. Enrollment itself grants neither paid access nor payment success.")
    @ApiResponse(responseCode = "201", description = "New pending enrollment")
    @ApiResponse(responseCode = "200", description = "Previously created enrollment receipt replayed")
    @ApiResponse(responseCode = "409", description = "Concurrent enrollment could not be reconciled")
    public ResponseEntity<EnrollmentResponse> enroll(@RequestBody CreateEnrollmentRequest request) {
        try {
            var result = subscriptions.enroll(request);
            return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(result.response());
        } catch (DataIntegrityViolationException race) {
            // A concurrent same-key enrollment won the unique constraint. Its
            // committed receipt is now authoritative; never create a second record.
            return subscriptions.replayReceipt(request.planId(), request.version(), request.operationKey())
                .map(response -> ResponseEntity.ok(response))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                    "Concurrent enrollment could not be reconciled"));
        }
    }

    @GetMapping
    @Operation(operationId = "subscriptionsList", summary = "List owned enrollments", description = "Requires an authenticated bearer session. Returns an unpaginated array scoped by the subscription service to the current owner and tenant. Does not accept a caller-selected owner/tenant.")
    public ResponseEntity<List<EnrollmentResponse>> enrollments() {
        return ResponseEntity.ok(subscriptions.enrollments());
    }

    @GetMapping("/{id}")
    @Operation(operationId = "subscriptionsGet", summary = "Get an owned enrollment", description = "Requires an authenticated bearer session. id is the enrollment UUID. Service-side owner/tenant checks prevent reading another account's enrollment.")
    public ResponseEntity<EnrollmentResponse> enrollment(@PathVariable UUID id) {
        return ResponseEntity.ok(subscriptions.enrollment(id));
    }

    @PostMapping("/{id}/cancel")
    @Operation(operationId = "subscriptionsCancel", summary = "Cancel an owned enrollment", description = "Requires an authenticated bearer session and ownership in the active tenant. No body is required. Returns the saved enrollment; this is not a refund or proof of external billing-provider cancellation.")
    public ResponseEntity<EnrollmentResponse> cancel(@PathVariable UUID id) {
        return ResponseEntity.ok(subscriptions.cancel(id));
    }

    @PostMapping("/{id}/grants")
    @Operation(operationId = "subscriptionsGrant", summary = "Record an administrative grant", description = "Requires system.admin in addition to service-side enrollment validation. Accepts the typed GrantRequest for the enrollment UUID. This privileged route must not be exposed by the customer BFF.")
    public ResponseEntity<com.surf.tracker.subscription.domain.AdminGrant> grant(
            @PathVariable UUID id, @RequestBody GrantRequest request) {
        return ResponseEntity.ok(subscriptions.grant(id, request));
    }

    @PostMapping("/{id}/consumptions")
    @Operation(operationId = "subscriptionsConsume", summary = "Record trusted usage", description = "Requires system.admin in addition to service-side enrollment validation. Accepts typed UsageRequest and records consumption for the UUID enrollment. Customer-controlled usage assertions are not trusted through the BFF.")
    public ResponseEntity<com.surf.tracker.subscription.domain.Subscription.Consumption> consume(
            @PathVariable UUID id, @RequestBody UsageRequest request) {
        return ResponseEntity.ok(subscriptions.consume(id, request));
    }
}
