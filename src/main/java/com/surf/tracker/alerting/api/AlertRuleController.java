package com.surf.tracker.alerting.api;

import com.surf.tracker.main.api.ApiV1;

import com.surf.tracker.alerting.api.dto.*;
import com.surf.tracker.alerting.application.service.AlertRuleService;
import com.surf.tracker.alerting.domain.enums.AlertRuleType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;

@RestController
@ApiV1
@RequestMapping("/alerts/rules")
@Tag(name = "Alert Rules", description = "Alert rule management endpoints")
public class AlertRuleController {

    private final AlertRuleService ruleService;

    public AlertRuleController(AlertRuleService ruleService) {
        this.ruleService = ruleService;
    }

    // ========== CRUD ENDPOINTS ==========

    @PostMapping
    @Operation(operationId = "alertRulesCreate", summary = "Create a new custom alert rule", description = "Requires alerts.write. Validates rule fields and references within the current tenant. Audit creator is the authenticated database user ID, not created_by from the request. Creation does not prove an evaluation event or notification delivery.")
    @ApiResponse(responseCode = "201", description = "Rule created")
    public ResponseEntity<AlertRuleResponse> createRule(@Valid @RequestBody CreateAlertRuleRequest request,
                                                       Principal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ruleService.createRule(request, authenticatedActor(principal)));
    }

    @GetMapping
    @Operation(operationId = "alertRulesSearch", summary = "Search alert rules", description = "Requires alerts.read. Combines optional rule-name search, ruleType and enabled filters within the tenant. Defaults: page=0, size=20, sortBy=createdAt, sortDirection=DESC. Returns PagedResponse; /list instead returns an unpaginated array.")
    public ResponseEntity<PagedResponse<AlertRuleResponse>> getAllRules(
            @Parameter(description = "Page number (0-based)")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Page size")
            @RequestParam(defaultValue = "20") int size,

            @Parameter(description = "Sort field")
            @RequestParam(defaultValue = "createdAt") String sortBy,

            @Parameter(description = "Sort direction")
            @RequestParam(defaultValue = "DESC") Sort.Direction sortDirection,

            @Parameter(description = "Search term for rule name")
            @RequestParam(required = false) String search,

            @Parameter(description = "Filter by rule type")
            @RequestParam(required = false) AlertRuleType ruleType,

            @Parameter(description = "Filter by enabled status")
            @RequestParam(required = false) Boolean enabled) {

        SearchRequest searchRequest = new SearchRequest();
        searchRequest.setPage(page);
        searchRequest.setSize(size);
        searchRequest.setSortBy(sortBy);
        searchRequest.setSortDirection(sortDirection);
        searchRequest.setSearch(search);
        searchRequest.setRuleType(ruleType);
        searchRequest.setEnabled(enabled);

        return ResponseEntity.ok(ruleService.getAllRulesPaged(searchRequest));
    }

    @GetMapping("/enabled")
    @Operation(operationId = "alertRulesEnabled", summary = "Get enabled alert rules with pagination", description = "Requires alerts.read. Forces enabled=true and optionally searches rule names. Defaults: page=0, size=20, sortBy=priority, sortDirection=DESC. Returns PagedResponse, distinct from /enabled/list.")
    public ResponseEntity<PagedResponse<AlertRuleResponse>> getEnabledRules(
            @Parameter(description = "Page number (0-based)")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Page size")
            @RequestParam(defaultValue = "20") int size,

            @Parameter(description = "Sort field")
            @RequestParam(defaultValue = "priority") String sortBy,

            @Parameter(description = "Sort direction")
            @RequestParam(defaultValue = "DESC") Sort.Direction sortDirection,

            @Parameter(description = "Search term for rule name")
            @RequestParam(required = false) String search) {

        SearchRequest searchRequest = new SearchRequest();
        searchRequest.setPage(page);
        searchRequest.setSize(size);
        searchRequest.setSortBy(sortBy);
        searchRequest.setSortDirection(sortDirection);
        searchRequest.setSearch(search);
        searchRequest.setEnabled(true);

        return ResponseEntity.ok(ruleService.getEnabledRulesPaged(searchRequest));
    }

    @GetMapping("/list")
    @Operation(operationId = "alertRulesList", summary = "Get alert rules without pagination", description = "Requires alerts.read. Returns all rules visible to the current tenant as a JSON array. This is a distinct unpaginated response shape, not an alias for the paginated collection route.")
    public ResponseEntity<List<AlertRuleResponse>> getAllRulesList() {
        return ResponseEntity.ok(ruleService.getAllRules());
    }

    @GetMapping("/enabled/list")
    @Operation(operationId = "alertRulesEnabledList", summary = "Get enabled alert rules without pagination", description = "Requires alerts.read. Returns enabled rules in the active tenant as an unpaginated JSON array. Does not change rule state.")
    public ResponseEntity<List<AlertRuleResponse>> getEnabledRulesList() {
        return ResponseEntity.ok(ruleService.getEnabledRules());
    }

    @GetMapping("/{ruleKey}")
    @Operation(operationId = "alertRulesGet", summary = "Get alert rule by key", description = "Requires alerts.read. ruleKey is the string rule key, not a numeric ID. Foreign or missing tenant rules are not exposed.")
    @ApiResponse(responseCode = "404", description = "Rule not found in the active tenant")
    @ApiResponse(responseCode = "200", description = "Tenant-authorized rule")
    public ResponseEntity<AlertRuleResponse> getRuleByKey(@PathVariable String ruleKey) {
        return ResponseEntity.ok(ruleService.getRuleByKey(ruleKey));
    }

    @PutMapping("/{ruleKey}")
    @Operation(operationId = "alertRulesUpdate", summary = "Update an alert rule", description = "Requires alerts.write. Applies supported update fields to the tenant-visible ruleKey. Existing creation audit fields remain server-owned. Reference and parameter checks are performed by the rule service.")
    public ResponseEntity<AlertRuleResponse> updateRule(
            @PathVariable String ruleKey,
            @Valid @RequestBody UpdateAlertRuleRequest request) {
        return ResponseEntity.ok(ruleService.updateRule(ruleKey, request));
    }

    @DeleteMapping("/{ruleKey}")
    @Operation(operationId = "alertRulesDelete", summary = "Delete an alert rule", description = "Requires alerts.write. Deletes the tenant-visible ruleKey and applies the service's cache invalidation. Success is 204 without a body.")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRule(@PathVariable String ruleKey) {
        ruleService.deleteRule(ruleKey);
    }

    @PatchMapping("/{ruleKey}/enable")
    @Operation(operationId = "alertRulesEnable", summary = "Enable an alert rule", description = "Requires alerts.write. No request body is required. Revalidates linked references before enabling, including active geofence and authorized linked vehicles for geofence rules. Returns HTTP 200 with no body, not an updated rule object.")
    public ResponseEntity<Void> enableRule(@PathVariable String ruleKey) {
        ruleService.enableRule(ruleKey);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{ruleKey}/disable")
    @Operation(operationId = "alertRulesDisable", summary = "Disable an alert rule", description = "Requires alerts.write. No request body is required. Disables the tenant-visible rule and invalidates relevant caches. Returns HTTP 200 with no body.")
    public ResponseEntity<Void> disableRule(@PathVariable String ruleKey) {
        ruleService.disableRule(ruleKey);
        return ResponseEntity.ok().build();
    }

    // ========== TEMPLATE ENDPOINTS ==========

    @PostMapping("/templates/overspeed")
    @Operation(operationId = "alertRulesCreateOverspeed", summary = "Create an overspeed rule", description = "Requires alerts.write. Validates the template's speed limit and tenant-owned vehicle references. Audit creator comes from the authenticated principal. Template configuration does not establish a received telemetry event.")
    @ApiResponse(responseCode = "201", description = "Overspeed rule created")
    public ResponseEntity<AlertRuleResponse> createOverspeedRule(
            @Valid @RequestBody OverspeedRuleTemplateRequest request, Principal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ruleService.createOverspeedRule(request, authenticatedActor(principal)));
    }

    @PostMapping("/templates/idle-timeout")
    @Operation(operationId = "alertRulesCreateIdleTimeout", summary = "Create an idle-timeout rule", description = "Requires alerts.write. Validates maximum idle minutes and template references, then creates a rule with the authenticated database user as creator.")
    @ApiResponse(responseCode = "201", description = "Idle-timeout rule created")
    public ResponseEntity<AlertRuleResponse> createIdleTimeoutRule(
            @Valid @RequestBody IdleTimeoutRuleTemplateRequest request, Principal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ruleService.createIdleTimeoutRule(request, authenticatedActor(principal)));
    }

    @PostMapping("/templates/geofence")
    @Operation(operationId = "alertRulesCreateGeofence", summary = "Create a geofence rule", description = "Requires alerts.write. Validates an active tenant-owned geofence, linked authorized vehicles and action ENTRY, EXIT or BOTH. Evaluation uses received locations and the existing per-rule/vehicle cooldown. Rule creation does not prove notification delivery.")
    @ApiResponse(responseCode = "201", description = "Geofence rule created")
    public ResponseEntity<AlertRuleResponse> createGeofenceRule(
            @Valid @RequestBody GeofenceRuleTemplateRequest request, Principal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ruleService.createGeofenceRule(request, authenticatedActor(principal)));
    }

    // ========== BATCH OPERATIONS ==========

    @PostMapping("/batch")
    @Operation(operationId = "alertRulesBatchCreate", summary = "Create alert rules in batch", description = "Requires alerts.write. Body is an array of CreateAlertRuleRequest values, not an object wrapper. The same authenticated creator is applied to all requests. Returns the created rules as a JSON array.")
    @ApiResponse(responseCode = "201", description = "Created rule array")
    public ResponseEntity<List<AlertRuleResponse>> batchCreateRules(
            @Valid @RequestBody List<CreateAlertRuleRequest> requests, Principal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ruleService.batchCreateRules(requests, authenticatedActor(principal)));
    }

    private String authenticatedActor(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new AccessDeniedException("An authenticated alert rule actor is required");
        }
        // JwtAuthFilter supplies the database user ID, never request-created_by.
        return principal.getName();
    }

    @PostMapping("/batch/enable")
    @Operation(operationId = "alertRulesBatchEnable", summary = "Enable multiple alert rules", description = "Requires alerts.write. Accepts a JSON array of string rule keys; the controller deduplicates keys before service validation. Tenant/reference checks remain authoritative. Returns HTTP 200 with no body.")
    public ResponseEntity<Void> batchEnableRules(@RequestBody List<String> ruleKeys) {
        ruleService.batchEnableRules(new java.util.HashSet<>(ruleKeys));
        return ResponseEntity.ok().build();
    }
}
