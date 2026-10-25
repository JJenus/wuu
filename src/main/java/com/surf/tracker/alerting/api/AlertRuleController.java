package com.surf.tracker.alerting.api;

import com.surf.tracker.alerting.api.dto.*;
import com.surf.tracker.alerting.application.service.AlertRuleService;
import com.surf.tracker.alerting.domain.enums.AlertRuleType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/alerts/rules")
@Tag(name = "Alert Rules", description = "Alert rule management endpoints")
public class AlertRuleController {

    private final AlertRuleService ruleService;

    public AlertRuleController(AlertRuleService ruleService) {
        this.ruleService = ruleService;
    }

    // ========== CRUD ENDPOINTS ==========

    @PostMapping
    @Operation(summary = "Create a new custom alert rule")
    public ResponseEntity<AlertRuleResponse> createRule(@Valid @RequestBody CreateAlertRuleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ruleService.createRule(request));
    }

    @GetMapping
    @Operation(summary = "Get all alert rules with pagination and filtering")
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
    @Operation(summary = "Get all enabled alert rules with pagination")
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
    @Operation(summary = "Get all alert rules (without pagination)")
    public ResponseEntity<List<AlertRuleResponse>> getAllRulesList() {
        return ResponseEntity.ok(ruleService.getAllRules());
    }

    @GetMapping("/enabled/list")
    @Operation(summary = "Get all enabled alert rules (without pagination)")
    public ResponseEntity<List<AlertRuleResponse>> getEnabledRulesList() {
        return ResponseEntity.ok(ruleService.getEnabledRules());
    }

    @GetMapping("/{ruleKey}")
    @Operation(summary = "Get alert rule by key")
    public ResponseEntity<AlertRuleResponse> getRuleByKey(@PathVariable String ruleKey) {
        return ResponseEntity.ok(ruleService.getRuleByKey(ruleKey));
    }

    @PutMapping("/{ruleKey}")
    @Operation(summary = "Update an existing alert rule")
    public ResponseEntity<AlertRuleResponse> updateRule(
            @PathVariable String ruleKey,
            @Valid @RequestBody UpdateAlertRuleRequest request) {
        return ResponseEntity.ok(ruleService.updateRule(ruleKey, request));
    }

    @DeleteMapping("/{ruleKey}")
    @Operation(summary = "Delete an alert rule")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRule(@PathVariable String ruleKey) {
        ruleService.deleteRule(ruleKey);
    }

    @PatchMapping("/{ruleKey}/enable")
    @Operation(summary = "Enable an alert rule")
    public ResponseEntity<Void> enableRule(@PathVariable String ruleKey) {
        ruleService.enableRule(ruleKey);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{ruleKey}/disable")
    @Operation(summary = "Disable an alert rule")
    public ResponseEntity<Void> disableRule(@PathVariable String ruleKey) {
        ruleService.disableRule(ruleKey);
        return ResponseEntity.ok().build();
    }

    // ========== TEMPLATE ENDPOINTS ==========

    @PostMapping("/templates/overspeed")
    @Operation(summary = "Create overspeed rule from template")
    public ResponseEntity<AlertRuleResponse> createOverspeedRule(
            @Valid @RequestBody OverspeedRuleTemplateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ruleService.createOverspeedRule(request));
    }

    @PostMapping("/templates/idle-timeout")
    @Operation(summary = "Create idle timeout rule from template")
    public ResponseEntity<AlertRuleResponse> createIdleTimeoutRule(
            @Valid @RequestBody IdleTimeoutRuleTemplateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ruleService.createIdleTimeoutRule(request));
    }

    @PostMapping("/templates/geofence")
    @Operation(summary = "Create geofence rule from template")
    public ResponseEntity<AlertRuleResponse> createGeofenceRule(
            @Valid @RequestBody GeofenceRuleTemplateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ruleService.createGeofenceRule(request));
    }

    // ========== BATCH OPERATIONS ==========

    @PostMapping("/batch")
    @Operation(summary = "Create multiple alert rules in batch")
    public ResponseEntity<List<AlertRuleResponse>> batchCreateRules(
            @Valid @RequestBody List<CreateAlertRuleRequest> requests) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ruleService.batchCreateRules(requests));
    }

    @PostMapping("/batch/enable")
    @Operation(summary = "Enable multiple alert rules")
    public ResponseEntity<Void> batchEnableRules(@RequestBody List<String> ruleKeys) {
        ruleService.batchEnableRules(new java.util.HashSet<>(ruleKeys));
        return ResponseEntity.ok().build();
    }
}