package com.surf.tracker.alerting.api;

import com.surf.tracker.main.api.ApiV1;

import com.surf.tracker.alerting.api.dto.*;
import com.surf.tracker.alerting.application.service.AlertService;
import com.surf.tracker.alerting.domain.enums.AlertSeverity;
import com.surf.tracker.alerting.domain.enums.AlertType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.time.Clock;
import java.time.Instant;
import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
@ApiV1
@RequestMapping("/alerts")
@Tag(name = "Alerts", description = "Alert management endpoints")
public class AlertController {

    private final AlertService alertService;
    private final Clock clock;

    public AlertController(AlertService alertService, Clock clock) {
        this.alertService = alertService;
        this.clock = clock;
    }

    // ========== CRUD ENDPOINTS ==========

    @PostMapping
    @Operation(operationId = "alertsCreate", summary = "Create a new alert", description = "Requires alerts.write. Validates the alert request and creates it within the authenticated tenant through the alert service. This is explicit alert creation, not evidence that hardware or rule evaluation triggered an event.")
    @ApiResponse(responseCode = "201", description = "Alert created")
    public ResponseEntity<AlertResponse> createAlert(@Valid @RequestBody CreateAlertRequest request) {
        AlertResponse response = alertService.createAlert(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{alertId}")
    @Operation(operationId = "alertsGet", summary = "Get alert by ID", description = "Requires alerts.read. alertId is numeric and resolved within the current tenant. Missing or foreign alerts are not exposed.")
    @ApiResponse(responseCode = "404", description = "Alert not found in the active tenant")
    @ApiResponse(responseCode = "200", description = "Tenant-authorized alert")
    public ResponseEntity<AlertResponse> getAlert(@PathVariable Long alertId) {
        AlertResponse response = alertService.getAlertById(alertId);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @Operation(operationId = "alertsSearch", summary = "Search alerts with pagination and filtering", description = "Requires alerts.read. Combines optional message search, vehicle/tracker, type, severity, acknowledgement, resolution and ISO-8601 date filters within the current tenant. Defaults: page=0, size=20, sortBy=triggeredAt, sortDirection=DESC. Returns the project's PagedResponse; no matches is an empty page, not an upstream-error fallback.")
    public ResponseEntity<PagedResponse<AlertResponse>> searchAlerts(
            @Parameter(description = "Page number (0-based)")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Page size")
            @RequestParam(defaultValue = "20") int size,

            @Parameter(description = "Sort field")
            @RequestParam(defaultValue = "triggeredAt") String sortBy,

            @Parameter(description = "Sort direction")
            @RequestParam(defaultValue = "DESC") Sort.Direction sortDirection,

            @Parameter(description = "Search in message")
            @RequestParam(required = false) String search,

            @Parameter(description = "Filter by vehicle ID")
            @RequestParam(required = false) String vehicleId,

            @Parameter(description = "Filter by tracker ID")
            @RequestParam(required = false) String trackerId,

            @Parameter(description = "Filter by alert type")
            @RequestParam(required = false) AlertType alertType,

            @Parameter(description = "Filter by severity")
            @RequestParam(required = false) AlertSeverity severity,

            @Parameter(description = "Filter by acknowledged status")
            @RequestParam(required = false) Boolean acknowledged,

            @Parameter(description = "Filter by resolved status")
            @RequestParam(required = false) Boolean resolved,

            @Parameter(description = "Start date (ISO format)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,

            @Parameter(description = "End date (ISO format)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate) {

        AlertSearchRequest searchRequest = new AlertSearchRequest();
        searchRequest.setPage(page);
        searchRequest.setSize(size);
        searchRequest.setSortBy(sortBy);
        searchRequest.setSortDirection(sortDirection);
        searchRequest.setSearch(search);
        searchRequest.setVehicleId(vehicleId);
        searchRequest.setTrackerId(trackerId);
        searchRequest.setAlertType(alertType);
        searchRequest.setSeverity(severity);
        searchRequest.setAcknowledged(acknowledged);
        searchRequest.setResolved(resolved);
        searchRequest.setStartDate(startDate);
        searchRequest.setEndDate(endDate);

        PagedResponse<AlertResponse> response = alertService.searchAlerts(searchRequest);
        return ResponseEntity.ok(response);
    }

    // ========== ALERT MANAGEMENT ==========

    @PostMapping("/{alertId}/acknowledge")
    @Operation(operationId = "alertsAcknowledge", summary = "Acknowledge an alert", description = "Requires alerts.write. Send a JSON object ({} is sufficient). The controller overwrites acknowledged_by with the authenticated database user ID; caller-selected actor values cannot determine audit identity. Returns the saved tenant-authorized alert.")
    public ResponseEntity<AlertResponse> acknowledgeAlert(
            @PathVariable Long alertId,
            @Valid @RequestBody AcknowledgeAlertRequest request,
            Principal principal) {
        request.setAcknowledgedBy(authenticatedActor(principal));
        AlertResponse response = alertService.acknowledgeAlert(alertId, request);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{alertId}")
    @Operation(operationId = "alertsUpdate", summary = "Update an alert", description = "Requires alerts.write. Applies supported UpdateAlertRequest fields through the tenant-scoped service. This does not permit changing tenant ownership or selecting the actor for dedicated acknowledgement/resolution operations.")
    public ResponseEntity<AlertResponse> updateAlert(
            @PathVariable Long alertId,
            @Valid @RequestBody UpdateAlertRequest request) {
        AlertResponse response = alertService.updateAlert(alertId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{alertId}")
    @Operation(operationId = "alertsDelete", summary = "Delete an alert", description = "Requires alerts.write. Deletes the numeric alert ID within the current tenant; returns 204 without a response body.")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAlert(@PathVariable Long alertId) {
        alertService.deleteAlert(alertId);
    }

    @PostMapping("/{alertId}/resolve")
    @Operation(operationId = "alertsResolve", summary = "Resolve an alert", description = "Requires alerts.write. JSON resolution_notes is required and must be 10–1000 characters. resolved_by is always overwritten with the authenticated database user ID. Returns the saved tenant-authorized alert.")
    @ApiResponse(responseCode = "400", description = "Invalid resolution notes or request")
    @ApiResponse(responseCode = "200", description = "Saved alert resolution and audit actor")
    public ResponseEntity<AlertResponse> resolveAlert(
            @PathVariable Long alertId,
            @Valid @RequestBody ResolveAlertRequest request,
            Principal principal) {
        request.setResolvedBy(authenticatedActor(principal));
        AlertResponse response = alertService.resolveAlert(alertId, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/bulk/acknowledge")
    @Operation(operationId = "alertsBulkAcknowledge", summary = "Bulk acknowledge alerts", description = "Requires alerts.write. Body is a JSON array of numeric alert IDs, not an object wrapper. Audit actor comes from the authenticated principal. Applies the service's tenant checks; success is HTTP 200 with no response body.")
    public ResponseEntity<Void> bulkAcknowledgeAlerts(
            @RequestBody List<Long> alertIds,
            Principal principal) {
        alertService.bulkAcknowledgeAlerts(alertIds, authenticatedActor(principal));
        return ResponseEntity.ok().build();
    }

    @PostMapping("/bulk/resolve")
    @Operation(operationId = "alertsBulkResolve", summary = "Bulk resolve alerts", description = "Requires alerts.write. Body is an array of numeric alert IDs; resolutionNotes is a required query parameter (camelCase), unlike the single-alert JSON resolution_notes field. Actor comes from the authenticated principal. Success is HTTP 200 with no response body.")
    public ResponseEntity<Void> bulkResolveAlerts(
            @RequestBody List<Long> alertIds,
            @RequestParam String resolutionNotes,
            Principal principal) {
        alertService.bulkResolveAlerts(alertIds, authenticatedActor(principal), resolutionNotes);
        return ResponseEntity.ok().build();
    }

    private String authenticatedActor(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new AccessDeniedException("An authenticated alert actor is required");
        }
        // JwtAuthFilter installs AuthPrincipal: its name is the database user ID,
        // not a client-supplied display name, role claim or tenant override.
        return principal.getName();
    }

    // ========== STATUS ENDPOINTS ==========

    @GetMapping("/active")
    @Operation(operationId = "alertsActive", summary = "Get active alerts with pagination", description = "Requires alerts.read. Returns active tenant alerts through the service in the project pagination envelope. Zero-based page defaults to 0 and size to 20; this is a read of persisted state, not a subscription stream.")
    public ResponseEntity<PagedResponse<AlertResponse>> getActiveAlerts(
            @Parameter(description = "Page number (0-based)")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Page size")
            @RequestParam(defaultValue = "20") int size) {

        PagedResponse<AlertResponse> response = alertService.getActiveAlertsPaged(page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/vehicle/{vehicleId}/active")
    @Operation(operationId = "alertsVehicleActive", summary = "Get active alerts for a vehicle", description = "Requires alerts.read. Uses the string vehicleId within the tenant scope. Returns an unpaginated JSON array of active alerts, not PagedResponse.")
    public ResponseEntity<List<AlertResponse>> getVehicleActiveAlerts(@PathVariable String vehicleId) {
        List<AlertResponse> response = alertService.getActiveAlerts(vehicleId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/vehicle/{vehicleId}/recent")
    @Operation(operationId = "alertsVehicleRecent", summary = "Get recent alerts for a vehicle", description = "Requires alerts.read. Returns recent tenant-visible vehicle alerts as a JSON array, limited by limit (default 10). vehicleId is the business string identifier.")
    public ResponseEntity<List<AlertResponse>> getVehicleRecentAlerts(
            @PathVariable String vehicleId,
            @Parameter(description = "Maximum number of alerts to return")
            @RequestParam(defaultValue = "10") int limit) {

        List<AlertResponse> response = alertService.getRecentAlerts(vehicleId, limit);
        return ResponseEntity.ok(response);
    }

    // ========== STATISTICS ENDPOINTS ==========

    @GetMapping("/stats")
    @Operation(operationId = "alertsStatistics", summary = "Get alert statistics", description = "Requires alerts.read. Optional startDate is an ISO-8601 instant; omitted start defaults to seven days before the server clock. End is the current server time. Returns the service's named count values within the current tenant.")
    public ResponseEntity<Map<String, Long>> getAlertStatistics(
            @Parameter(description = "Start date for statistics (ISO format)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate) {

        Instant start = startDate != null ? startDate : clock.instant().minusSeconds(7 * 24 * 60 * 60); // Default: last 7 days
        Map<String, Long> stats = alertService.getAlertStatistics(start, clock.instant());
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/stats/unacknowledged")
    @Operation(operationId = "alertsUnacknowledgedCounts", summary = "Get count of unacknowledged alerts by severity", description = "Requires alerts.read. Returns a JSON object of severity-to-count entries for the current tenant. This response is an aggregate object, not a list or page of alerts.")
    public ResponseEntity<Map<String, Long>> getUnacknowledgedCounts() {
        Map<String, Long> counts = alertService.getUnacknowledgedCountBySeverity();
        return ResponseEntity.ok(counts);
    }

    @GetMapping("/vehicle/{vehicleId}/has-critical")
    @Operation(operationId = "alertsVehicleHasCritical", summary = "Check if vehicle has critical unacknowledged alerts", description = "Requires alerts.read. Returns a JSON boolean from the tenant-scoped vehicle alert query, not a wrapped status object. Does not acknowledge or resolve alerts.")
    public ResponseEntity<Boolean> hasCriticalUnacknowledgedAlerts(@PathVariable String vehicleId) {
        boolean hasCritical = alertService.hasCriticalUnacknowledgedAlerts(vehicleId);
        return ResponseEntity.ok(hasCritical);
    }

    // ========== MAINTENANCE ENDPOINTS ==========

    @PostMapping("/cleanup")
    @Operation(operationId = "alertsCleanup", summary = "Disabled HTTP alert cleanup", description = "SecurityConfig denies this operation for every HTTP caller, including administrators. cutoffTime is an ISO-8601 instant but this mapping is not an available maintenance API.", responses = @ApiResponse(responseCode = "403", description = "HTTP cleanup explicitly disabled"))
    public ResponseEntity<Void> cleanupStaleAlerts(
            @Parameter(description = "Cutoff time for stale alerts (ISO format)")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant cutoffTime) {
        alertService.cleanupStaleAlerts(cutoffTime);
        return ResponseEntity.ok().build();
    }

    // ========== AUTOMATED ALERT ENDPOINTS ==========

    @PostMapping("/automated")
    @Operation(operationId = "alertsAutomated", summary = "Disabled HTTP automated-alert input", description = "SecurityConfig denies this operation for every HTTP caller. Automated alerts must come from the trusted internal evaluation/event flow, not caller-supplied metadata. Metadata is a legacy dynamic event boundary, not a new general-purpose DTO contract.", responses = @ApiResponse(responseCode = "403", description = "HTTP automated input explicitly disabled"))
    public ResponseEntity<AlertResponse> createAutomatedAlert(
            @RequestParam String vehicleId,
            @RequestParam String trackerId,
            @RequestParam AlertType alertType,
            @RequestParam AlertSeverity severity,
            @RequestParam String message,
            @RequestBody(required = false) Map<String, Object> metadata) {

        alertService.processAutomatedAlert(vehicleId, trackerId, alertType, severity, message, metadata);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}
