package com.surf.tracker.subscription.api;

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
@RequestMapping("/api/v1/subscriptions")
public class SubscriptionController {

    private final SubscriptionService subscriptions;

    public SubscriptionController(SubscriptionService subscriptions) {
        this.subscriptions = subscriptions;
    }

    @PostMapping("/catalog")
    public ResponseEntity<PlanVersionResponse> createPlan(@RequestBody CreatePlanRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(subscriptions.createPlan(request));
    }

    @GetMapping("/catalog")
    public ResponseEntity<List<PlanVersionResponse>> catalog() {
        return ResponseEntity.ok(subscriptions.catalog());
    }

    @GetMapping("/catalog/{planId}/{version}")
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
    public ResponseEntity<List<EnrollmentResponse>> enrollments() {
        return ResponseEntity.ok(subscriptions.enrollments());
    }

    @GetMapping("/{id}")
    public ResponseEntity<EnrollmentResponse> enrollment(@PathVariable UUID id) {
        return ResponseEntity.ok(subscriptions.enrollment(id));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<EnrollmentResponse> cancel(@PathVariable UUID id) {
        return ResponseEntity.ok(subscriptions.cancel(id));
    }
}
