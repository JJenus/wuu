package com.surf.tracker.core.api;

import com.surf.tracker.main.api.ApiV1;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

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
@ApiV1
@RequestMapping("/trackers")
@Tag(name = "Trackers", description = "Tenant-authorized tracker inventory. Registration is not proof of transport or hardware connectivity.")
public class TrackerController {

    private final TrackerService trackerService;
    private final com.surf.tracker.core.application.service.PhonePairingService pairing;
    private final Clock clock;

    public TrackerController(TrackerService trackerService,
                             com.surf.tracker.core.application.service.PhonePairingService pairing, Clock clock) {
        this.trackerService = trackerService;
        this.pairing = pairing;
        this.clock = clock;
    }

    @GetMapping
    @Operation(operationId = "trackersList", summary = "List tenant trackers", description = "Requires devices.read. Page defaults: page=0, size=20, sortBy=createdAt, sortDirection=DESC. Filters are NOT combined: status takes precedence over online, then batteryThreshold, then stale=true. staleMinutes defaults to 10. Without a selected filter returns all authorized trackers.")
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
    @Operation(operationId = "trackersGet", summary = "Get a tenant tracker", description = "Requires devices.read. trackerId is the string business identifier. Returns registration and last saved telemetry/status fields; absence does not fall back to another tenant's inventory.")
    @ApiResponse(responseCode = "404", description = "Tracker not found in the authorized scope")
    @ApiResponse(responseCode = "200", description = "Tracker registration and last saved status")
    public ResponseEntity<TrackerResponse> getTracker(@PathVariable String trackerId) {
        Tracker tracker = trackerService.getTrackerById(trackerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tracker not found"));
        return ResponseEntity.ok(toResponse(tracker));
    }

    @PostMapping
    @Operation(operationId = "trackersCreate", summary = "Register and assign a tracker", description = "Requires vehicles.assign_device. Requires tracker_id, device_id and an authorized vehicle_id. New status is ACTIVE; hardware/device uniqueness and assignment are validated by the tracker service. Optional protocol/model/SIM metadata does not establish a transport session.")
    @ApiResponse(responseCode = "201", description = "Tracker registered and assigned")
    @ApiResponse(responseCode = "400", description = "Invalid registration or assignment")
    @ApiResponse(responseCode = "409", description = "Device, tracker or assignment conflicts with existing data")
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

    @PostMapping("/{trackerId}/pairing-token")
    @Operation(operationId = "trackersPairingToken", summary = "Issue a phone/browser pairing token", description = "Requires vehicles.assign_device. Only for trackers whose protocol is PHONE. The token lets one phone or browser report positions as this tracker until it expires; pairing_url opens the tracking page with the token already filled in when PHONE_PUBLIC_URL is configured. The page must be opened over https, because browsers only expose location on secure origins; http://localhost on the same machine also works for development, and a phone needs an https tunnel or proxy (see device-comm/PHONE-TRACKING.md). The page connects to the device-comm WebSocket /ingest, sends the token, then one JSON fix per message (lat, lng, speed_kmh, ts in epoch ms; optional accuracy_m, battery_pct). The token is stateless and not stored: it cannot be revoked individually, only by expiry (PHONE_PAIRING_TTL_MINUTES) or by rotating PHONE_PAIRING_SECRET.")
    @ApiResponse(responseCode = "200", description = "Token issued")
    @ApiResponse(responseCode = "404", description = "Tracker not found in the authorized scope")
    @ApiResponse(responseCode = "409", description = "Tracker protocol is not PHONE")
    @ApiResponse(responseCode = "503", description = "Phone pairing is not configured on this server")
    public ResponseEntity<PairingTokenResponse> issuePairingToken(@PathVariable String trackerId) {
        Tracker tracker = trackerService.getTrackerById(trackerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tracker not found"));
        if (!com.surf.tracker.core.application.service.PhonePairingService.isPhone(tracker))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only trackers with protocol PHONE can be paired");
        if (!pairing.isConfigured())
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Phone pairing is not configured");
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(pairing.issue(tracker));
    }

    @PatchMapping("/{trackerId}/profile")
    @Operation(operationId = "trackersProfile", summary = "Edit tracker registration metadata", description = "Requires vehicles.assign_device. Replaces model, protocol, firmware_version and sim_number only; omitted fields clear metadata. Device ID, assignment and measured status are preserved.")
    public ResponseEntity<TrackerResponse> updateProfile(@PathVariable String trackerId,
            @jakarta.validation.Valid @RequestBody TrackerProfileRequest request) {
        return ResponseEntity.ok(toResponse(trackerService.updateProfile(trackerId, request)));
    }

    @PostMapping("/{trackerId}/reassign")
    @Operation(operationId = "trackersReassign", summary = "Correct an unused tracker assignment", description = "Requires vehicles.assign_device and an active tenant. Explicit source_vehicle_id must match current assignment; target_vehicle_id must belong to the same tenant. Recorded locations, raw data, device activity, commands, assignment history or an active trip on either vehicle prevent a move (409). No unassignment or cross-tenant transfer. Device identity is immutable.")
    @ApiResponse(responseCode = "409", description = "Tracker used, active trip, or stale assignment")
    @ApiResponse(responseCode = "404", description = "Tracker or destination not found in tenant scope")
    public ResponseEntity<TrackerResponse> reassign(@PathVariable String trackerId,
            @jakarta.validation.Valid @RequestBody ReassignTrackerRequest request) {
        return ResponseEntity.ok(toResponse(trackerService.reassign(trackerId, request)));
    }

    @PutMapping("/{trackerId}")
    @Operation(operationId = "trackersUpdate", summary = "Update tracker fields", description = "Requires vehicles.assign_device. Accepts model, protocol, firmware_version, sim_number, battery_level, signal_strength and status through the tracker service. Does not take a new device ID or vehicle assignment.")
    @ApiResponse(responseCode = "404", description = "Tracker not found")
    @ApiResponse(responseCode = "200", description = "Updated tracker")
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
    @Operation(operationId = "trackersDelete", summary = "Delete a tracker", description = "Requires vehicles.assign_device. Deletes the tenant-authorized tracker through the service; returns 204 without a body.")
    @ApiResponse(responseCode = "404", description = "Tracker not found")
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
