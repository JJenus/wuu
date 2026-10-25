package com.surf.tracker.core.api;

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
@RequestMapping("/api/v1/trips")
public class TripController {

    private final TripQueryService tripQueryService;

    public TripController(TripQueryService tripQueryService) {
        this.tripQueryService = tripQueryService;
    }

    @GetMapping
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
    public ResponseEntity<TripResponse> getTrip(@PathVariable String tripId) {
        Trip trip = tripQueryService.getTripById(tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found"));
        return ResponseEntity.ok(toResponse(trip));
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
