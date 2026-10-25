package com.surf.tracker.alerting.api;

import com.surf.tracker.alerting.api.dto.*;
import com.surf.tracker.alerting.application.service.GeofenceService;
import com.surf.tracker.alerting.domain.entity.Geofence;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/alerts/geofences")
@Tag(name = "Geofences", description = "Geofence management endpoints")
public class GeofenceController {

    private final GeofenceService geofenceService;

    public GeofenceController(GeofenceService geofenceService) {
        this.geofenceService = geofenceService;
    }

    @PostMapping
    @Operation(summary = "Create a new geofence")
    public ResponseEntity<GeofenceResponse> createGeofence(@Valid @RequestBody CreateGeofenceRequest request) {
        Geofence geofence = convertToEntity(request);
        Geofence created = geofenceService.createGeofence(geofence);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(created));
    }

    @GetMapping
    @Operation(summary = "Search geofences with pagination and filtering")
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
    @Operation(summary = "Get all geofences for a vehicle with pagination")
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
    @Operation(summary = "Get all geofences for a vehicle (without pagination)")
    public ResponseEntity<List<GeofenceResponse>> getVehicleGeofencesList(@PathVariable String vehicleId) {
        List<Geofence> geofences = geofenceService.getVehicleGeofences(vehicleId);
        return ResponseEntity.ok(geofences.stream()
                .map(this::toResponse)
                .collect(Collectors.toList()));
    }

    @GetMapping("/vehicle/{vehicleId}/active")
    @Operation(summary = "Get active geofences for a vehicle with pagination")
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
    @Operation(summary = "Get active geofences for a vehicle (without pagination)")
    public ResponseEntity<List<GeofenceResponse>> getActiveVehicleGeofencesList(@PathVariable String vehicleId) {
        List<Geofence> geofences = geofenceService.getActiveGeofences(vehicleId);
        return ResponseEntity.ok(geofences.stream()
                .map(this::toResponse)
                .collect(Collectors.toList()));
    }

    @GetMapping("/{geofenceId}")
    @Operation(summary = "Get geofence by ID")
    public ResponseEntity<GeofenceResponse> getGeofenceById(@PathVariable Long geofenceId) {
        Geofence geofence = geofenceService.getGeofenceById(geofenceId);
        return ResponseEntity.ok(toResponse(geofence));
    }

    @PutMapping("/{geofenceId}")
    @Operation(summary = "Update a geofence")
    public ResponseEntity<GeofenceResponse> updateGeofence(
            @PathVariable Long geofenceId,
            @Valid @RequestBody UpdateGeofenceRequest request) {
        Geofence updates = convertToEntity(request);
        Geofence updated = geofenceService.updateGeofence(geofenceId, updates);
        return ResponseEntity.ok(toResponse(updated));
    }

    @DeleteMapping("/{geofenceId}")
    @Operation(summary = "Delete a geofence")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteGeofence(@PathVariable Long geofenceId) {
        geofenceService.deleteGeofence(geofenceId);
    }

    @PostMapping("/check-violations")
    @Operation(summary = "Check for geofence violations (for testing)")
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
        geofence.setVehicleIds(new java.util.HashSet<>());
        geofence.getVehicleIds().add(request.getVehicleId());
        geofence.setName(request.getName());
        geofence.setShapeType(request.getShapeType());
        geofence.setCenterLatitude(request.getCenterLatitude());
        geofence.setCenterLongitude(request.getCenterLongitude());
        geofence.setRadiusMeters(request.getRadiusMeters());
        geofence.setIsActive(request.isActive());
        geofence.setCreatedBy(request.getCreatedBy());

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
        geofence.setIsActive(request.isActive());
        geofence.setShapeType(request.getShapeType());
        geofence.setCenterLatitude(request.getCenterLatitude());
        geofence.setCenterLongitude(request.getCenterLongitude());
        geofence.setRadiusMeters(request.getRadiusMeters());
        return geofence;
    }

    private GeofenceResponse toResponse(Geofence geofence) {
        GeofenceResponse response = new GeofenceResponse();
        response.setGeofenceId(geofence.getGeofenceId());
        response.setVehicleId(geofence.getVehicleIds().stream().findFirst().orElse(null));
        response.setName(geofence.getName());
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