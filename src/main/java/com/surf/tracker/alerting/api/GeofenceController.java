package com.surf.tracker.alerting.api;

import com.surf.tracker.main.api.ApiV1;

import com.surf.tracker.alerting.api.dto.*;
import com.surf.tracker.alerting.application.service.GeofenceService;
import com.surf.tracker.alerting.domain.entity.Geofence;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.security.access.AccessDeniedException;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@ApiV1
@RequestMapping("/alerts/geofences")
@Tag(name = "Geofences", description = "Geofence management endpoints")
public class GeofenceController {

    private final GeofenceService geofenceService;

    public GeofenceController(GeofenceService geofenceService) {
        this.geofenceService = geofenceService;
    }

    @PostMapping
    @Operation(operationId = "geofencesCreate", summary = "Create a geofence", description = "Requires alerts.write and an authenticated actor. Creates geometry and vehicle associations atomically. vehicle_ids is authoritative when supplied; otherwise vehicle_id is the legacy singular input. CIRCLE uses center/radius; POLYGON uses ordered points. Creator comes from the principal, not created_by. Active defaults to true.")
    @ApiResponse(responseCode = "201", description = "Geofence and associations created")
    @ApiResponse(responseCode = "400", description = "Invalid geometry or vehicle associations")
    public ResponseEntity<GeofenceResponse> createGeofence(@Valid @RequestBody CreateGeofenceRequest request,
                                                          Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new AccessDeniedException("An authenticated geofence actor is required");
        }
        Geofence geofence = convertToEntity(request);
        // JwtAuthFilter installs the database user ID, never a browser-supplied display name.
        geofence.setCreatedBy(principal.getName());
        Geofence created = geofenceService.createGeofence(geofence);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(created));
    }

    @GetMapping
    @Operation(operationId = "geofencesSearch", summary = "Search geofences", description = "Requires alerts.read. Combines optional name search, vehicleId and active filters within the current tenant. Defaults: page=0, size=20, sortBy=createdAt, sortDirection=DESC. Returns PagedResponse with complete vehicle_ids associations.")
    public ResponseEntity<PagedResponse<GeofenceResponse>> searchGeofences(
            @Parameter(description = "Page number (0-based)")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Page size")
            @RequestParam(defaultValue = "20") int size,

            @Parameter(description = "Sort field")
            @RequestParam(defaultValue = "createdAt") String sortBy,

            @Parameter(description = "Sort direction")
            @RequestParam(defaultValue = "DESC") Sort.Direction sortDirection,

            @Parameter(description = "Search term for geofence name")
            @RequestParam(required = false) String search,

            @Parameter(description = "Filter by vehicle ID")
            @RequestParam(required = false) String vehicleId,

            @Parameter(description = "Filter by active status")
            @RequestParam(required = false) Boolean active) {

        SearchRequest searchRequest = new SearchRequest();
        searchRequest.setPage(page);
        searchRequest.setSize(size);
        searchRequest.setSortBy(sortBy);
        searchRequest.setSortDirection(sortDirection);
        searchRequest.setSearch(search);
        searchRequest.setVehicleId(vehicleId);
        searchRequest.setActive(active);

        return ResponseEntity.ok(geofenceService.searchGeofences(searchRequest));
    }

    @GetMapping("/vehicle/{vehicleId}")
    @Operation(operationId = "geofencesVehiclePage", summary = "Get a vehicle's geofences", description = "Requires alerts.read. Returns tenant-authorized vehicle associations in PagedResponse. Defaults: page=0, size=20, sortBy=name, sortDirection=ASC. Includes active and inactive boundaries; /list returns an unpaginated array instead.")
    public ResponseEntity<PagedResponse<GeofenceResponse>> getVehicleGeofences(
            @PathVariable String vehicleId,
            @Parameter(description = "Page number (0-based)")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Page size")
            @RequestParam(defaultValue = "20") int size,

            @Parameter(description = "Sort field")
            @RequestParam(defaultValue = "name") String sortBy,

            @Parameter(description = "Sort direction")
            @RequestParam(defaultValue = "ASC") Sort.Direction sortDirection) {

        SearchRequest searchRequest = new SearchRequest();
        searchRequest.setPage(page);
        searchRequest.setSize(size);
        searchRequest.setSortBy(sortBy);
        searchRequest.setSortDirection(sortDirection);

        return ResponseEntity.ok(geofenceService.getVehicleGeofencesPaged(vehicleId, searchRequest));
    }

    @GetMapping("/vehicle/{vehicleId}/list")
    @Operation(operationId = "geofencesVehicleList", summary = "Get a vehicle's geofences without pagination", description = "Requires alerts.read. Returns an unpaginated JSON array of tenant-visible boundaries associated with the string vehicleId. This is not the response shape of the paginated vehicle route.")
    public ResponseEntity<List<GeofenceResponse>> getVehicleGeofencesList(@PathVariable String vehicleId) {
        List<Geofence> geofences = geofenceService.getVehicleGeofences(vehicleId);
        return ResponseEntity.ok(geofences.stream()
                .map(this::toResponse)
                .collect(Collectors.toList()));
    }

    @GetMapping("/vehicle/{vehicleId}/active")
    @Operation(operationId = "geofencesVehicleActivePage", summary = "Get active vehicle geofences", description = "Requires alerts.read. Returns only active tenant-visible boundaries for the string vehicleId in PagedResponse. Defaults: page=0, size=20, sortBy=name, sortDirection=ASC.")
    public ResponseEntity<PagedResponse<GeofenceResponse>> getActiveVehicleGeofences(
            @PathVariable String vehicleId,
            @Parameter(description = "Page number (0-based)")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Page size")
            @RequestParam(defaultValue = "20") int size,

            @Parameter(description = "Sort field")
            @RequestParam(defaultValue = "name") String sortBy,

            @Parameter(description = "Sort direction")
            @RequestParam(defaultValue = "ASC") Sort.Direction sortDirection) {

        SearchRequest searchRequest = new SearchRequest();
        searchRequest.setPage(page);
        searchRequest.setSize(size);
        searchRequest.setSortBy(sortBy);
        searchRequest.setSortDirection(sortDirection);

        return ResponseEntity.ok(geofenceService.getActiveGeofencesPaged(vehicleId, searchRequest));
    }

    @GetMapping("/vehicle/{vehicleId}/active/list")
    @Operation(operationId = "geofencesVehicleActiveList", summary = "Get active vehicle geofences without pagination", description = "Requires alerts.read. Returns an unpaginated array of active tenant-visible boundaries associated with vehicleId. Reading boundaries does not run rule evaluation.")
    public ResponseEntity<List<GeofenceResponse>> getActiveVehicleGeofencesList(@PathVariable String vehicleId) {
        List<Geofence> geofences = geofenceService.getActiveGeofences(vehicleId);
        return ResponseEntity.ok(geofences.stream()
                .map(this::toResponse)
                .collect(Collectors.toList()));
    }

    @GetMapping("/{geofenceId}")
    @Operation(operationId = "geofencesGet", summary = "Get a geofence", description = "Requires alerts.read. Numeric geofenceId is resolved in the current tenant. vehicle_ids contains the complete sorted association set; singular vehicle_id is only the first associated vehicle when available.")
    @ApiResponse(responseCode = "404", description = "Geofence not found in the active tenant")
    @ApiResponse(responseCode = "200", description = "Geofence with complete vehicle associations")
    public ResponseEntity<GeofenceResponse> getGeofenceById(@PathVariable Long geofenceId) {
        Geofence geofence = geofenceService.getGeofenceById(geofenceId);
        return ResponseEntity.ok(toResponse(geofence));
    }

    @PutMapping("/{geofenceId}")
    @Operation(operationId = "geofencesUpdate", summary = "Update a geofence", description = "Requires alerts.write. Omitted vehicle_ids preserves existing associations; an empty array explicitly unlinks every vehicle. Omitted geometry fields are passed to service merge rules, not zero-filled by the controller. Geometry and associations are validated before persistence; creator remains server-owned.")
    @ApiResponse(responseCode = "400", description = "Invalid geometry or associations")
    @ApiResponse(responseCode = "404", description = "Geofence not found in the active tenant")
    @ApiResponse(responseCode = "200", description = "Updated geometry and associations")
    public ResponseEntity<GeofenceResponse> updateGeofence(
            @PathVariable Long geofenceId,
            @Valid @RequestBody UpdateGeofenceRequest request) {
        Geofence updates = convertToEntity(request);
        Geofence updated = geofenceService.updateGeofence(geofenceId, updates);
        return ResponseEntity.ok(toResponse(updated));
    }

    @DeleteMapping("/{geofenceId}")
    @Operation(operationId = "geofencesDelete", summary = "Delete a geofence", description = "Requires alerts.write. Deletes the numeric tenant-authorized boundary through the service. Success is 204 without a response body.")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteGeofence(@PathVariable Long geofenceId) {
        geofenceService.deleteGeofence(geofenceId);
    }

    @PostMapping("/check-violations")
    @Operation(operationId = "geofencesCheckViolations", summary = "Evaluate a supplied vehicle position", description = "Requires alerts.write. Accepts vehicleId, latitude and longitude as query parameters and invokes geofence violation checking. This is a mutating diagnostic/evaluation action, not a read-only geometry lookup or proof of device telemetry ingestion. Success is HTTP 200 without a body.")
    public ResponseEntity<Void> checkGeofenceViolations(
            @RequestParam String vehicleId,
            @RequestParam Double latitude,
            @RequestParam Double longitude) {
        geofenceService.checkGeofenceViolations(vehicleId, latitude, longitude);
        return ResponseEntity.ok().build();
    }

    // ========== HELPER METHODS ==========

    private Geofence convertToEntity(CreateGeofenceRequest request) {
        Geofence geofence = new Geofence();
        if (request.getVehicleIds() != null) {
            geofence.setVehicleIds(new java.util.HashSet<>(request.getVehicleIds()));
        } else if (request.getVehicleId() != null && !request.getVehicleId().isBlank()) {
            geofence.setVehicleIds(new java.util.HashSet<>(java.util.Set.of(request.getVehicleId())));
        } else {
            throw new IllegalArgumentException("Vehicle associations are required");
        }
        geofence.setName(request.getName());
        geofence.setDescription(request.getDescription());
        geofence.setShapeType(request.getShapeType());
        geofence.setCenterLatitude(request.getCenterLatitude());
        geofence.setCenterLongitude(request.getCenterLongitude());
        geofence.setRadiusMeters(request.getRadiusMeters());
        geofence.setIsActive(request.isActive());

        // Add polygon points if provided
        if (request.getPoints() != null) {
            for (int i = 0; i < request.getPoints().size(); i++) {
                GeofencePointDto point = request.getPoints().get(i);
                geofence.addPoint(point.getLatitude(), point.getLongitude(), i + 1);
            }
        }

        return geofence;
    }

    private Geofence convertToEntity(UpdateGeofenceRequest request) {
        Geofence geofence = new Geofence();
        geofence.setName(request.getName());
        geofence.setDescription(request.getDescription());
        // Null means omitted; an empty set explicitly removes all associations.
        geofence.setVehicleIds(request.getVehicleIds());
        geofence.setIsActive(request.isActive());
        geofence.setShapeType(request.getShapeType());
        geofence.setCenterLatitude(request.getCenterLatitude());
        geofence.setCenterLongitude(request.getCenterLongitude());
        geofence.setRadiusMeters(request.getRadiusMeters());
        if (request.getPoints() == null) {
            geofence.setPoints(null);
        } else {
            for (int i = 0; i < request.getPoints().size(); i++) {
                GeofencePointDto point = request.getPoints().get(i);
                geofence.addPoint(point.getLatitude(), point.getLongitude(), i + 1);
            }
        }
        return geofence;
    }

    private GeofenceResponse toResponse(Geofence geofence) {
        GeofenceResponse response = new GeofenceResponse();
        response.setGeofenceId(geofence.getGeofenceId());
        List<String> vehicleIds = geofence.getVehicleIds().stream().sorted().toList();
        response.setVehicleIds(vehicleIds);
        response.setVehicleId(vehicleIds.stream().findFirst().orElse(null));
        response.setName(geofence.getName());
        response.setDescription(geofence.getDescription());
        response.setShapeType(geofence.getShapeType());
        response.setCenterLatitude(geofence.getCenterLatitude());
        response.setCenterLongitude(geofence.getCenterLongitude());
        response.setRadiusMeters(geofence.getRadiusMeters());
        response.setActive(Boolean.TRUE.equals(geofence.getIsActive()));
        response.setCreatedAt(geofence.getCreatedAt());
        response.setUpdatedAt(geofence.getUpdatedAt());

        // Convert points
        if (geofence.getPoints() != null && !geofence.getPoints().isEmpty()) {
            response.setPoints(geofence.getPoints().stream()
                    .map(point -> {
                        GeofencePointDto dto = new GeofencePointDto();
                        dto.setLatitude(point.getLatitude());
                        dto.setLongitude(point.getLongitude());
                        return dto;
                    })
                    .collect(Collectors.toList()));
        }

        return response;
    }
}
