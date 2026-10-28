package com.surf.tracker.core.application;

import com.surf.tracker.core.domain.entity.Vehicle;
import com.surf.tracker.core.infrastructure.repository.VehicleRepository;
import com.surf.tracker.shared.domain.LocationPoint;
import com.surf.tracker.shared.security.TenantContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class VehicleQueryService {
    private final VehicleRepository vehicleRepository;

    public VehicleQueryService(VehicleRepository vehicleRepository) {
        this.vehicleRepository = vehicleRepository;
    }

    public Optional<Vehicle> getVehicleById(String vehicleId) {
        Long orgId = TenantContext.getCurrentOrgId();
        return vehicleRepository.findByIdAndOrganizationId(vehicleId, orgId);
    }

    public Page<Vehicle> getVehicles(Pageable pageable) {
        Long orgId = TenantContext.getCurrentOrgId();
        return vehicleRepository.findByOrganizationId(orgId, pageable);
    }

    public Optional<LocationPoint> getCurrentLocation(String vehicleId) {
        return getVehicleById(vehicleId)
            .map(Vehicle::getCurrentLocationPoint);
    }

    public boolean isVehicleMoving(String vehicleId) {
        return getVehicleById(vehicleId)
            .map(v -> {
                LocationPoint loc = v.getCurrentLocationPoint();
                return loc != null && loc.speedKmh() > 0;
            })
            .orElse(false);
    }

    public Optional<Float> getVehicleSpeed(String vehicleId) {
        return getVehicleById(vehicleId)
            .map(v -> {
                LocationPoint loc = v.getCurrentLocationPoint();
                return loc != null ? loc.speedKmh() : null;
            });
    }
}
