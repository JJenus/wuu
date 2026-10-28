package com.surf.tracker.alerting.api;

import com.surf.tracker.alerting.api.dto.*;
import com.surf.tracker.alerting.application.service.AlertService;
import com.surf.tracker.alerting.domain.enums.AlertSeverity;
import com.surf.tracker.alerting.domain.enums.AlertType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
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
    @Operation(summary = "Create a new alert")
    public ResponseEntity<AlertResponse> createAlert(@Valid @RequestBody CreateAlertRequest request) {
        AlertResponse response = alertService.createAlert(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{alertId}")
    @Operation(summary = "Get alert by ID")
    public ResponseEntity<AlertResponse> getAlert(@PathVariable Long alertId) {
        AlertResponse response = alertService.getAlertById(alertId);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @Operation(summary = "Search alerts with pagination and filtering")
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
    @Operation(summary = "Acknowledge an alert")
    public ResponseEntity<AlertResponse> acknowledgeAlert(
            @PathVariable Long alertId,
            @Valid @RequestBody AcknowledgeAlertRequest request) {
        AlertResponse response = alertService.acknowledgeAlert(alertId, request);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{alertId}")
    @Operation(summary = "Update an alert")
    public ResponseEntity<AlertResponse> updateAlert(
            @PathVariable Long alertId,
            @Valid @RequestBody UpdateAlertRequest request) {
        AlertResponse response = alertService.updateAlert(alertId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{alertId}")
    @Operation(summary = "Delete an alert")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAlert(@PathVariable Long alertId) {
        alertService.deleteAlert(alertId);
    }

    @PostMapping("/{alertId}/resolve")
    @Operation(summary = "Resolve an alert")
    public ResponseEntity<AlertResponse> resolveAlert(
            @PathVariable Long alertId,
            @Valid @RequestBody ResolveAlertRequest request) {
        AlertResponse response = alertService.resolveAlert(alertId, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/bulk/acknowledge")
    @Operation(summary = "Bulk acknowledge alerts")
    public ResponseEntity<Void> bulkAcknowledgeAlerts(
            @RequestBody List<Long> alertIds,
            @RequestParam String acknowledgedBy) {
        alertService.bulkAcknowledgeAlerts(alertIds, acknowledgedBy);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/bulk/resolve")
    @Operation(summary = "Bulk resolve alerts")
    public ResponseEntity<Void> bulkResolveAlerts(
            @RequestBody List<Long> alertIds,
            @RequestParam String resolvedBy,
            @RequestParam String resolutionNotes) {
        alertService.bulkResolveAlerts(alertIds, resolvedBy, resolutionNotes);
        return ResponseEntity.ok().build();
    }

    // ========== STATUS ENDPOINTS ==========

    @GetMapping("/active")
    @Operation(summary = "Get active alerts with pagination")
    public ResponseEntity<PagedResponse<AlertResponse>> getActiveAlerts(
            @Parameter(description = "Page number (0-based)")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Page size")
            @RequestParam(defaultValue = "20") int size) {

        PagedResponse<AlertResponse> response = alertService.getActiveAlertsPaged(page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/vehicle/{vehicleId}/active")
    @Operation(summary = "Get active alerts for a vehicle")
    public ResponseEntity<List<AlertResponse>> getVehicleActiveAlerts(@PathVariable String vehicleId) {
        List<AlertResponse> response = alertService.getActiveAlerts(vehicleId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/vehicle/{vehicleId}/recent")
    @Operation(summary = "Get recent alerts for a vehicle")
    public ResponseEntity<List<AlertResponse>> getVehicleRecentAlerts(
            @PathVariable String vehicleId,
            @Parameter(description = "Maximum number of alerts to return")
            @RequestParam(defaultValue = "10") int limit) {

        List<AlertResponse> response = alertService.getRecentAlerts(vehicleId, limit);
        return ResponseEntity.ok(response);
    }

    // ========== STATISTICS ENDPOINTS ==========

    @GetMapping("/stats")
    @Operation(summary = "Get alert statistics")
    public ResponseEntity<Map<String, Long>> getAlertStatistics(
            @Parameter(description = "Start date for statistics (ISO format)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate) {

        Instant start = startDate != null ? startDate : clock.instant().minusSeconds(7 * 24 * 60 * 60); // Default: last 7 days
        Map<String, Long> stats = alertService.getAlertStatistics(start, clock.instant());
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/stats/unacknowledged")
    @Operation(summary = "Get count of unacknowledged alerts by severity")
    public ResponseEntity<Map<String, Long>> getUnacknowledgedCounts() {
        Map<String, Long> counts = alertService.getUnacknowledgedCountBySeverity();
        return ResponseEntity.ok(counts);
    }

    @GetMapping("/vehicle/{vehicleId}/has-critical")
    @Operation(summary = "Check if vehicle has critical unacknowledged alerts")
    public ResponseEntity<Boolean> hasCriticalUnacknowledgedAlerts(@PathVariable String vehicleId) {
        boolean hasCritical = alertService.hasCriticalUnacknowledgedAlerts(vehicleId);
        return ResponseEntity.ok(hasCritical);
    }

    // ========== MAINTENANCE ENDPOINTS ==========

    @PostMapping("/cleanup")
    @Operation(summary = "Clean up stale alerts (admin only)")
    public ResponseEntity<Void> cleanupStaleAlerts(
            @Parameter(description = "Cutoff time for stale alerts (ISO format)")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant cutoffTime) {
        alertService.cleanupStaleAlerts(cutoffTime);
        return ResponseEntity.ok().build();
    }

    // ========== AUTOMATED ALERT ENDPOINTS ==========

    @PostMapping("/automated")
    @Operation(summary = "Create an automated alert (for internal use)")
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
