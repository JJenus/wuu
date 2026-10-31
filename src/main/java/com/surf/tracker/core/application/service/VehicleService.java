package com.surf.tracker.core.application.service;

import com.surf.tracker.core.domain.entity.*;
import com.surf.tracker.core.domain.enums.TripEndReason;
import com.surf.tracker.core.domain.enums.TripStartReason;
import com.surf.tracker.core.infrastructure.repository.*;
import com.surf.tracker.shared.domain.LocationPoint;
import com.surf.tracker.shared.util.TimeProvider;
import com.surf.tracker.shared.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
public class VehicleService {

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    private static final Logger logger = LoggerFactory.getLogger(VehicleService.class);

    private final VehicleRepository vehicleRepository;
    private final TrackerRepository trackerRepository;
    private final TripRepository tripRepository;
    private final TrackerLocationRepository locationRepository;

    public VehicleService(VehicleRepository vehicleRepository,
                          TrackerRepository trackerRepository,
                          TripRepository tripRepository,
                          TrackerLocationRepository locationRepository) {
        this.vehicleRepository = vehicleRepository;
        this.trackerRepository = trackerRepository;
        this.tripRepository = tripRepository;
        this.locationRepository = locationRepository;
    }

    @Transactional
    public Vehicle createVehicle(Vehicle vehicle) {
        Long organizationId = TenantContext.getCurrentOrgId();
        if (organizationId == null || organizationId <= 0) {
            throw new IllegalArgumentException("Organization identity is required");
        }
        if (vehicle.getVehicleId() == null || vehicle.getVehicleId().isBlank()) {
            throw new IllegalArgumentException("Vehicle ID is required");
        }
        String deviceId = vehicle.getDeviceId() == null ? null : vehicle.getDeviceId().trim();
        if (deviceId != null && deviceId.isEmpty()) {
            deviceId = null;
        }
        vehicle.setDeviceId(deviceId);
        if (deviceId != null && trackerRepository.existsByDeviceId(deviceId)) {
            // The vehicle and tracker tables share one physical device namespace.
            throw new org.springframework.dao.DuplicateKeyException(
                    "Device is already registered to a tracker: " + deviceId);
        }
        vehicle.setOrganizationId(organizationId);
        // Assigned identifiers must be inserted, never merged over an existing tenant's graph.
        entityManager.persist(vehicle);
        entityManager.flush();
        return vehicle;
    }

    @Transactional
    public Vehicle saveVehicle(Vehicle vehicle) {
        if (vehicle.getOrganizationId() == null) {
            vehicle.setOrganizationId(TenantContext.getCurrentOrgId());
        }
        return vehicleRepository.save(vehicle);
    }

    @Transactional
    public void updateVehicleAccStatus(String vehicleId, boolean accStatus, Instant timestamp) {
        Long orgId = TenantContext.getCurrentOrgId();
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
            .orElseThrow(() -> new IllegalArgumentException("Vehicle not found: " + vehicleId));

        if (!orgId.equals(vehicle.getOrganizationId())) {
            throw new IllegalArgumentException("Vehicle not found: " + vehicleId);
        }

        vehicle.updateAccStatus(accStatus, timestamp);
        vehicleRepository.save(vehicle);
    }

    @Transactional
    public void updateVehicleLocation(String vehicleId, String deviceId, LocationPoint locationPoint) {
        Long orgId = TenantContext.getCurrentOrgId();
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
            .orElseThrow(() -> new IllegalArgumentException("Vehicle not found: " + vehicleId));

        if (!orgId.equals(vehicle.getOrganizationId())) {
            throw new IllegalArgumentException("Vehicle not found: " + vehicleId);
        }

        TrackerLocation location = new TrackerLocation(
                locationPoint.latitude(),
                locationPoint.longitude(),
                locationPoint.speedKmh(),
                locationPoint.timestamp()
        );

        Tracker tracker = trackerRepository.findByDeviceIdAndOrganizationId(deviceId, orgId)
            .filter(candidate -> candidate.getVehicle() != null
                && vehicleId.equals(candidate.getVehicle().getVehicleId()))
            .orElseThrow(() -> new IllegalArgumentException("Device is not assigned to vehicle"));
        location.setTracker(tracker);
        tracker.updateLastSeen();
        trackerRepository.save(tracker);

        TrackerLocation saved = locationRepository.save(location);

        vehicle.updateLocation(saved);
        vehicleRepository.save(vehicle);

        logger.debug("Saved location for vehicle {}: lat={}, lon={}, speed={} km/h",
                vehicleId, locationPoint.latitude(), locationPoint.longitude(), locationPoint.speedKmh());
    }
    
    @Transactional
    public Trip startTrip(String vehicleId, TrackerLocation startLocation, TripStartReason reason) {
        Long orgId = TenantContext.getCurrentOrgId();
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
            .orElseThrow(() -> new IllegalArgumentException("Vehicle not found"));
        
        if (!orgId.equals(vehicle.getOrganizationId())) {
            throw new IllegalArgumentException("Vehicle not found");
        }
        
        Trip trip = new Trip();
        trip.setTripId("TRIP_" + vehicleId + "_" + TimeProvider.newId().substring(0, 8));
        trip.setVehicle(vehicle);
        trip.setStartLocation(startLocation);
        trip.setStartTime(startLocation.getRecordedAt());
        trip.setStartReason(reason);
        trip.setIsActive(true);
        
        locationRepository.save(startLocation);
        
        vehicle.addTrip(trip);
        vehicleRepository.save(vehicle);
        
        return tripRepository.save(trip);
    }
    
    @Transactional
    public void endTrip(String tripId, TrackerLocation endLocation, TripEndReason reason) {
        Trip trip = tripRepository.findById(tripId)
            .orElseThrow(() -> new IllegalArgumentException("Trip not found"));
        
        if (endLocation != null) {
            locationRepository.save(endLocation);
        }
        
        trip.endTrip(reason, endLocation);
        tripRepository.save(trip);
    }
    
    @Transactional(readOnly = true)
    public Optional<Vehicle> getVehicle(String vehicleId) {
        Long orgId = TenantContext.getCurrentOrgId();
        return vehicleRepository.findByIdAndOrganizationId(vehicleId, orgId);
    }
    
    @Transactional(readOnly = true)
    public Optional<Vehicle> getVehicleByDeviceId(String deviceId) {
        Long orgId = TenantContext.getCurrentOrgId();
        return vehicleRepository.findByDeviceIdAndOrganizationId(deviceId, orgId);
    }
    
    @Transactional
    public void deleteVehicle(String vehicleId) {
        Long orgId = TenantContext.getCurrentOrgId();
        if (vehicleRepository.findByIdAndOrganizationId(vehicleId, orgId).isEmpty()) {
            throw new IllegalArgumentException("Vehicle not found: " + vehicleId);
        }
        vehicleRepository.deleteByIdAndOrganizationId(vehicleId, orgId);
    }
    
    @Transactional(readOnly = true)
    public Optional<Trip> getActiveTrip(String vehicleId) {
        Long orgId = TenantContext.getCurrentOrgId();
        return tripRepository.findByVehicleVehicleIdAndIsActiveAndOrganizationId(vehicleId, true, orgId);
    }

    public String findVehicleIdForDevice(String deviceId) {
        Long orgId = TenantContext.getCurrentOrgId();
        Tracker tracker = trackerRepository.findByDeviceIdAndOrganizationId(deviceId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Tracker not found: " + deviceId));

        Vehicle vehicle = tracker.getVehicle();
        if (vehicle == null) {
            throw new IllegalStateException(
                    String.format("Tracker %s is not assigned to a vehicle", deviceId));
        }
        return vehicle.getVehicleId();
    }
}
