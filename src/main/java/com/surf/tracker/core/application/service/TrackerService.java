package com.surf.tracker.core.application.service;

import com.surf.tracker.core.application.DeviceIdentityGuard;
import com.surf.tracker.core.domain.entity.Tracker;
import com.surf.tracker.core.domain.entity.Vehicle;
import com.surf.tracker.core.domain.enums.TrackerStatus;
import com.surf.tracker.core.infrastructure.repository.TrackerRepository;
import com.surf.tracker.core.infrastructure.repository.VehicleRepository;
import com.surf.tracker.shared.security.TenantContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
public class TrackerService {

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    private final TrackerRepository trackerRepository;
    private final VehicleRepository vehicleRepository;
    private final DeviceIdentityGuard deviceIdentityGuard;

    public TrackerService(TrackerRepository trackerRepository, VehicleRepository vehicleRepository,
                          DeviceIdentityGuard deviceIdentityGuard) {
        this.trackerRepository = trackerRepository;
        this.vehicleRepository = vehicleRepository;
        this.deviceIdentityGuard = deviceIdentityGuard;
    }

    @Transactional(readOnly = true)
    public Page<Tracker> getTrackers(Pageable pageable) {
        return trackerRepository.findByOrganizationId(TenantContext.getCurrentOrgId(), pageable);
    }

    @Transactional(readOnly = true)
    public Optional<Tracker> getTrackerById(String trackerId) {
        return trackerRepository.findByIdAndOrganizationId(trackerId, TenantContext.getCurrentOrgId());
    }

    @Transactional(readOnly = true)
    public Page<Tracker> getByStatus(TrackerStatus status, Pageable pageable) {
        return trackerRepository.findByStatusAndOrganizationId(status, TenantContext.getCurrentOrgId(), pageable);
    }

    @Transactional(readOnly = true)
    public Page<Tracker> getOnline(boolean online, Pageable pageable) {
        return trackerRepository.findByIsOnlineAndOrganizationId(online, TenantContext.getCurrentOrgId(), pageable);
    }

    @Transactional(readOnly = true)
    public Page<Tracker> getLowBattery(float threshold, Pageable pageable) {
        return trackerRepository.findTrackersWithLowBattery(threshold, TenantContext.getCurrentOrgId(), pageable);
    }

    @Transactional(readOnly = true)
    public Page<Tracker> getStale(Instant cutoffTime, Pageable pageable) {
        return trackerRepository.findStaleConnections(cutoffTime, TenantContext.getCurrentOrgId(), pageable);
    }

    @Transactional
    public Tracker createTracker(Tracker tracker, String vehicleId) {
        Long orgId = TenantContext.getCurrentOrgId();
        if (orgId == null || orgId <= 0) throw new IllegalArgumentException("Organization identity is required");
        String trackerId = tracker.getTrackerId() == null ? null : tracker.getTrackerId().trim();
        if (trackerId == null || trackerId.isEmpty()) {
            throw new IllegalArgumentException("Tracker ID is required");
        }
        String normalizedVehicleId = vehicleId == null ? null : vehicleId.trim();
        if (normalizedVehicleId == null || normalizedVehicleId.isEmpty()) {
            throw new IllegalArgumentException("Vehicle ID is required");
        }
        String deviceId = tracker.getDeviceId() == null ? null : tracker.getDeviceId().trim();
        if (deviceId == null || deviceId.isEmpty()) {
            throw new IllegalArgumentException("Device ID is required");
        }
        tracker.setTrackerId(trackerId);
        tracker.setDeviceId(deviceId);
        // Validate ownership before taking a shared device lock so invalid callers cannot serialize writers.
        Vehicle vehicle = vehicleRepository.findByIdAndOrganizationId(normalizedVehicleId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Vehicle not found: " + normalizedVehicleId));
        tracker.setVehicle(vehicle);
        // Serialize with vehicle creation so both sides re-check after the same lock.
        deviceIdentityGuard.lockForRegistration(deviceId);
        if (trackerRepository.existsByDeviceId(deviceId) || vehicleRepository.existsByDeviceId(deviceId)) {
            // The vehicle and tracker tables share one physical device namespace.
            throw new org.springframework.dao.DuplicateKeyException(
                    "Device is already registered: " + deviceId);
        }
        entityManager.persist(tracker);
        entityManager.flush();
        return tracker;
    }

    @Transactional
    public Tracker updateTracker(String trackerId, Tracker update) {
        Long orgId = TenantContext.getCurrentOrgId();
        Tracker tracker = trackerRepository.findByIdAndOrganizationId(trackerId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Tracker not found: " + trackerId));

        tracker.setModel(update.getModel());
        tracker.setProtocol(update.getProtocol());
        tracker.setFirmwareVersion(update.getFirmwareVersion());
        tracker.setSimNumber(update.getSimNumber());
        tracker.setBatteryLevel(update.getBatteryLevel());
        tracker.setSignalStrength(update.getSignalStrength());
        tracker.setStatus(update.getStatus());
        return trackerRepository.save(tracker);
    }

    @Transactional
    public void deleteTracker(String trackerId) {
        Long orgId = TenantContext.getCurrentOrgId();
        if (trackerRepository.findByIdAndOrganizationId(trackerId, orgId).isEmpty()) {
            throw new IllegalArgumentException("Tracker not found: " + trackerId);
        }
        trackerRepository.deleteByIdAndOrganizationId(trackerId, orgId);
    }
}
