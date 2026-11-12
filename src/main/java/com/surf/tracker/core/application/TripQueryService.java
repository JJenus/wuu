package com.surf.tracker.core.application;

import com.surf.tracker.core.domain.entity.Trip;
import com.surf.tracker.core.infrastructure.repository.TripRepository;
import com.surf.tracker.core.infrastructure.repository.TrackerLocationRepository;
import com.surf.tracker.core.api.dto.TripHistoryResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.Clock;
import com.surf.tracker.shared.security.TenantContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class TripQueryService {
    private final TripRepository tripRepository;
    private final TrackerLocationRepository locations;
    private final Clock clock;

    public TripQueryService(TripRepository tripRepository, TrackerLocationRepository locations, Clock clock) {
        this.tripRepository = tripRepository;
        this.locations = locations;
        this.clock = clock;
    }

    public Page<Trip> getTrips(Pageable pageable) {
        return tripRepository.findByOrganizationId(TenantContext.getCurrentOrgId(), pageable);
    }

    public Optional<Trip> getTripById(String tripId) {
        return tripRepository.findByIdAndOrganizationId(tripId, TenantContext.getCurrentOrgId());
    }

    public TripHistoryResponse getHistory(String tripId, int limit) {
        Long organizationId = TenantContext.getCurrentOrgId();
        if (organizationId == null || organizationId <= 0)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Organization required");
        if (limit < 1 || limit > 10_000)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "History limit must be between 1 and 10000");
        Trip trip = getTripById(tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found"));
        var now = clock.instant();
        var end = trip.getEndTime() == null || trip.getEndTime().isAfter(now) ? now : trip.getEndTime();
        var points = locations.findRecordedWindow(trip.getVehicle().getVehicleId(), organizationId,
                trip.getStartTime(), end, PageRequest.of(0, limit + 1));
        return new TripHistoryResponse(tripId, trip.getVehicle().getVehicleId(),
                TripHistoryResponse.Source.VEHICLE_TIME_WINDOW, trip.getStartTime(), end,
                points.size() > limit, points.stream().limit(limit).map(p -> new TripHistoryResponse.Point(
                        p.getLocationId(), p.getRecordedAt(), p.getLatitude(), p.getLongitude(), p.getSpeedKmh())).toList());
    }
}
