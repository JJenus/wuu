package com.surf.tracker.core.application.service;

import com.surf.tracker.core.domain.entity.Tracker;
import com.surf.tracker.core.domain.entity.Vehicle;
import com.surf.tracker.core.domain.enums.TrackerStatus;
import com.surf.tracker.core.infrastructure.repository.TrackerRepository;
import com.surf.tracker.core.infrastructure.repository.VehicleRepository;
import com.surf.tracker.core.infrastructure.repository.TripRepository;
import com.surf.tracker.core.api.dto.TrackerProfileRequest;
import com.surf.tracker.core.api.dto.ReassignTrackerRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
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
    private final TripRepository tripRepository;

    public TrackerService(TrackerRepository trackerRepository, VehicleRepository vehicleRepository, TripRepository tripRepository) {
        this.trackerRepository = trackerRepository;
        this.vehicleRepository = vehicleRepository;
        this.tripRepository = tripRepository;
    }

    @Transactional
    public Tracker updateProfile(String trackerId, TrackerProfileRequest profile) {
        Tracker tracker = managedTracker(trackerId);
        tracker.setModel(profile.model()); tracker.setProtocol(profile.protocol());
        tracker.setFirmwareVersion(profile.firmwareVersion()); tracker.setSimNumber(profile.simNumber());
        return trackerRepository.saveAndFlush(tracker);
    }

    @Transactional
    public Tracker reassign(String trackerId, ReassignTrackerRequest request) {
        Tracker tracker = managedTracker(trackerId);
        Long orgId = TenantContext.getCurrentOrgId();
        String source = request.sourceVehicleId().trim(), target = request.targetVehicleId().trim();
        // Check destination scope before reporting conflicts, without exposing foreign inventory.
        if (vehicleRepository.findByIdAndOrganizationId(target, orgId).isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Vehicle not found");
        if (!tracker.getVehicle().getVehicleId().equals(source))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Assignment changed. Reload the tracker before retrying.");
        if (source.equals(target)) return tracker;
        // A consistent vehicle order prevents opposite moves from deadlocking each other.
        for (String id : java.util.stream.Stream.of(source, target).sorted().toList())
            vehicleRepository.findForTelemetry(id, orgId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vehicle not found"));
        if (tracker.getLastSeen() != null || Boolean.TRUE.equals(tracker.getIsOnline())
            || trackerRepository.hasLocations(trackerId) || trackerRepository.hasRawData(trackerId)
            || trackerRepository.hasCommands(trackerId) || trackerRepository.hasAssignmentHistory(trackerId))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only unused trackers can be reassigned. Recorded history or device activity must stay with its vehicle.");
        if (tripRepository.findByVehicleVehicleIdAndIsActiveAndOrganizationId(source, true, orgId).isPresent()
            || tripRepository.findByVehicleVehicleIdAndIsActiveAndOrganizationId(target, true, orgId).isPresent())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Neither vehicle may have an active trip when reassigning a tracker.");
        tracker.setVehicle(vehicleRepository.getReferenceById(target));
        return trackerRepository.saveAndFlush(tracker);
    }

    private Tracker managedTracker(String trackerId) {
        Long orgId = TenantContext.getCurrentOrgId();
        if (orgId == null || orgId <= 0)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Active organization required");
        return trackerRepository.findForManagement(trackerId, orgId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tracker not found"));
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
        // Validate ownership before attempting registration.
        Vehicle vehicle = vehicleRepository.findByIdAndOrganizationId(normalizedVehicleId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Vehicle not found: " + normalizedVehicleId));
        tracker.setVehicle(vehicle);
        // Friendly early rejection; database triggers enforce the invariant for concurrent/direct writers.
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
