package com.surf.tracker.core.api;

import com.surf.tracker.main.api.ApiV1;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.surf.tracker.core.api.dto.*;
import com.surf.tracker.core.application.TripQueryService;
import com.surf.tracker.core.domain.entity.Trip;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@ApiV1
@RequestMapping("/trips")
@Tag(name = "Trips", description = "Tenant-authorized persisted trip summaries, recorded fixes and CSV exports.")
public class TripController {

    private final TripQueryService tripQueryService;

    public TripController(TripQueryService tripQueryService) {
        this.tripQueryService = tripQueryService;
    }

    @GetMapping
    @Operation(operationId = "tripsList", summary = "List trip summaries", description = "Requires trips.read. Zero-based page defaults to 0, size to 20, sortBy to startTime and sortDirection to DESC. Returns only trips visible in the current tenant through the query service.")
    public ResponseEntity<PagedResponse<TripResponse>> listTrips(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "startTime") String sortBy,
            @RequestParam(defaultValue = "DESC") Sort.Direction sortDirection) {

        Page<Trip> trips = tripQueryService.getTrips(
                PageRequest.of(page, size, Sort.by(sortDirection, sortBy)));
        return ResponseEntity.ok(new PagedResponse<>(trips.map(this::toResponse)));
    }

    @GetMapping("/{tripId}")
    @Operation(operationId = "tripsGet", summary = "Get a trip summary", description = "Requires trips.read. tripId is the string trip identifier. Returns persisted start/end times, activity, distance, speed and reason fields; does not reconstruct a route from summary data.")
    @ApiResponse(responseCode = "404", description = "Trip not found in the authorized scope")
    @ApiResponse(responseCode = "200", description = "Persisted trip summary")
    public ResponseEntity<TripResponse> getTrip(@PathVariable String tripId) {
        Trip trip = tripQueryService.getTripById(tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found"));
        return ResponseEntity.ok(toResponse(trip));
    }

    @GetMapping("/{tripId}/history")
    @Operation(operationId = "tripsHistory", summary = "Get recorded fixes in a trip window", description = "Requires trips.read and a positive active tenant ID. limit must be 1–10000 (default 10000). Returns stored fixes for the trip's vehicle/time window and a truncated flag, not inferred trip membership or interpolated route geometry. Window end is capped at the current server time for active or future-ended trips.")
    @ApiResponse(responseCode = "400", description = "Invalid history limit or query")
    @ApiResponse(responseCode = "404", description = "Trip or authorized vehicle not found")
    @ApiResponse(responseCode = "200", description = "Recorded vehicle/time-window fixes with truncation metadata")
    public TripHistoryResponse getHistory(@PathVariable String tripId,
            @RequestParam(defaultValue = "10000") int limit) {
        return tripQueryService.getHistory(tripId, limit);
    }

    private TripResponse toResponse(Trip trip) {
        TripResponse response = new TripResponse();
        response.setTripId(trip.getTripId());
        response.setVehicleId(trip.getVehicle() != null ? trip.getVehicle().getVehicleId() : null);
        response.setStartTime(trip.getStartTime());
        response.setEndTime(trip.getEndTime());
        response.setIsActive(trip.getIsActive());
        response.setTotalDistanceKm(trip.getTotalDistanceKm());
        response.setAverageSpeedKmh(trip.getAverageSpeedKmh());
        response.setMaxSpeedKmh(trip.getMaxSpeedKmh());
        response.setFuelConsumedLiters(trip.getFuelConsumedLiters());
        response.setStartReason(trip.getStartReason() != null ? trip.getStartReason().name() : null);
        response.setEndReason(trip.getEndReason() != null ? trip.getEndReason().name() : null);
        return response;
    }
}
