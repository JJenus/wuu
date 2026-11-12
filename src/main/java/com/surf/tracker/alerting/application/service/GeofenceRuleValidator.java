package com.surf.tracker.alerting.application.service;

import com.surf.tracker.alerting.api.dto.GeofenceRuleTemplateRequest;
import com.surf.tracker.alerting.domain.entity.Geofence;
import com.surf.tracker.alerting.exception.AlertException;
import org.springframework.stereotype.Component;
import com.surf.tracker.core.infrastructure.repository.VehicleRepository;
import com.surf.tracker.shared.security.TenantContext;

@Component
public class GeofenceRuleValidator {

    private final GeofenceService geofenceService;
    private final VehicleRepository vehicles;

    public GeofenceRuleValidator(GeofenceService geofenceService, VehicleRepository vehicles) {
        this.geofenceService = geofenceService;
        this.vehicles = vehicles;
    }

    public void validateGeofenceRuleRequest(GeofenceRuleTemplateRequest request) {
        // Validate geofence exists and is active
        try {
            Long geofenceId = Long.parseLong(request.getGeofenceId());
            if (!geofenceService.existsAndActive(geofenceId)) {
                throw AlertException.geofenceNotFound(request.getGeofenceId());
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid geofence ID format");
        }

        // Validate vehicle IDs are not empty
        if (request.getVehicleIds() == null || request.getVehicleIds().isEmpty()) {
            throw new IllegalArgumentException("At least one vehicle ID is required");
        }
        Long organizationId = TenantContext.getCurrentOrgId();
        if (organizationId == null || organizationId <= 0) {
            throw new IllegalArgumentException("Organization identity is required");
        }
        for (String vehicleId : request.getVehicleIds()) {
            if (vehicleId == null || vehicleId.isBlank() || vehicleId.length() > 50
                    || vehicles.findByIdAndOrganizationId(vehicleId, organizationId).isEmpty()) {
                throw new IllegalArgumentException("Selected vehicle is not available in this workspace");
            }
        }
    }

    public Geofence getValidatedGeofence(String geofenceId) {
        try {
            Long id = Long.parseLong(geofenceId);
            Geofence geofence = geofenceService.getGeofenceById(id);

            if (geofence == null) {
                throw AlertException.geofenceNotFound(geofenceId);
            }

            if (!Boolean.TRUE.equals(geofence.getIsActive())) {
                throw new IllegalStateException("Geofence is not active: " + geofenceId);
            }

            return geofence;

        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid geofence ID format: " + geofenceId);
        }
    }
}
