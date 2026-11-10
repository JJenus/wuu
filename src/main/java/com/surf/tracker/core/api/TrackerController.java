package com.surf.tracker.core.api;

import com.surf.tracker.core.api.dto.*;
import com.surf.tracker.core.application.service.TrackerService;
import com.surf.tracker.core.domain.entity.Tracker;
import com.surf.tracker.core.domain.enums.TrackerStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;

@RestController
@RequestMapping("/api/v1/trackers")
public class TrackerController {

    private final TrackerService trackerService;
    private final Clock clock;

    public TrackerController(TrackerService trackerService, Clock clock) {
        this.trackerService = trackerService;
        this.clock = clock;
    }

    @GetMapping
    public ResponseEntity<PagedResponse<TrackerResponse>> listTrackers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") Sort.Direction sortDirection,
            @RequestParam(required = false) Boolean online,
            @RequestParam(required = false) TrackerStatus status,
            @RequestParam(required = false) Float batteryThreshold,
            @RequestParam(required = false) Boolean stale,
            @RequestParam(defaultValue = "10") int staleMinutes) {

        org.springframework.data.domain.Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sortBy));

        Page<Tracker> result;
        if (status != null) {
            result = trackerService.getByStatus(status, pageable);
        } else if (online != null) {
            result = trackerService.getOnline(online, pageable);
        } else if (batteryThreshold != null) {
            result = trackerService.getLowBattery(batteryThreshold, pageable);
        } else if (Boolean.TRUE.equals(stale)) {
            result = trackerService.getStale(clock.instant().minusSeconds(staleMinutes * 60L), pageable);
        } else {
            result = trackerService.getTrackers(pageable);
        }

        return ResponseEntity.ok(new PagedResponse<>(result.map(this::toResponse)));
    }

    @GetMapping("/{trackerId}")
    public ResponseEntity<TrackerResponse> getTracker(@PathVariable String trackerId) {
        Tracker tracker = trackerService.getTrackerById(trackerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tracker not found"));
        return ResponseEntity.ok(toResponse(tracker));
    }

    @PostMapping
    public ResponseEntity<TrackerResponse> createTracker(@jakarta.validation.Valid @RequestBody CreateTrackerRequest request) {
        Tracker tracker = new Tracker();
        tracker.setTrackerId(request.getTrackerId());
        tracker.setDeviceId(request.getDeviceId());
        tracker.setModel(request.getModel());
        tracker.setProtocol(request.getProtocol());
        tracker.setFirmwareVersion(request.getFirmwareVersion());
        tracker.setSimNumber(request.getSimNumber());
        tracker.setStatus(TrackerStatus.ACTIVE);

        try {
            Tracker created = trackerService.createTracker(tracker, request.getVehicleId());
            return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(created));
        } catch (org.springframework.dao.DataIntegrityViolationException
                 | jakarta.persistence.EntityExistsException
                 | org.hibernate.exception.ConstraintViolationException conflict) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Tracker conflicts with existing data");
        }
    }

    @PutMapping("/{trackerId}")
    public ResponseEntity<TrackerResponse> updateTracker(@PathVariable String trackerId,
                                                         @jakarta.validation.Valid @RequestBody UpdateTrackerRequest request) {
        Tracker update = new Tracker();
        update.setModel(request.getModel());
        update.setProtocol(request.getProtocol());
        update.setFirmwareVersion(request.getFirmwareVersion());
        update.setSimNumber(request.getSimNumber());
        update.setBatteryLevel(request.getBatteryLevel());
        update.setSignalStrength(request.getSignalStrength());
        update.setStatus(request.getStatus());

        try {
            Tracker updated = trackerService.updateTracker(trackerId, update);
            return ResponseEntity.ok(toResponse(updated));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Tracker not found");
        }
    }

    @DeleteMapping("/{trackerId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTracker(@PathVariable String trackerId) {
        try {
            trackerService.deleteTracker(trackerId);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Tracker not found");
        }
    }

    private TrackerResponse toResponse(Tracker tracker) {
        TrackerResponse response = new TrackerResponse();
        response.setTrackerId(tracker.getTrackerId());
        response.setDeviceId(tracker.getDeviceId());
        response.setVehicleId(tracker.getVehicle() != null ? tracker.getVehicle().getVehicleId() : null);
        response.setModel(tracker.getModel());
        response.setProtocol(tracker.getProtocol());
        response.setFirmwareVersion(tracker.getFirmwareVersion());
        response.setSimNumber(tracker.getSimNumber());
        response.setBatteryLevel(tracker.getBatteryLevel());
        response.setSignalStrength(tracker.getSignalStrength());
        response.setIsOnline(tracker.getIsOnline());
        response.setStatus(tracker.getStatus() != null ? tracker.getStatus().name() : null);
        response.setLastSeen(tracker.getLastSeen());
        response.setCreatedAt(tracker.getCreatedAt());
        response.setUpdatedAt(tracker.getUpdatedAt());
        return response;
    }
}
