package com.surf.tracker.core.application.service;

import com.surf.tracker.core.domain.entity.*;
import com.surf.tracker.core.domain.enums.TripEndReason;
import com.surf.tracker.core.domain.enums.TripStartReason;
import com.surf.tracker.core.infrastructure.repository.TripRepository;
import com.surf.tracker.core.infrastructure.repository.TripPointRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

/** Invoked only within the locked vehicle/location/receipt transaction. */
@Service
public class AutomaticTripService {
    private final TripRepository trips;
    private final TripPointRepository points;
    private final Clock clock;
    @Value("${tracking.trips.start-speed-kmh:5}") private float startSpeed = 5;
    @Value("${tracking.trips.stationary-speed-kmh:1}") private float stationarySpeed = 1;
    @Value("${tracking.trips.stop-seconds:300}") private int stopSeconds = 300;
    @Value("${tracking.trips.max-gap-seconds:900}") private int maxGapSeconds = 900;

    public AutomaticTripService(TripRepository trips, TripPointRepository points, Clock clock) {
        this.trips = trips; this.points = points; this.clock = clock;
    }

    @jakarta.annotation.PostConstruct
    void validatePolicy() {
        if (!Float.isFinite(startSpeed) || !Float.isFinite(stationarySpeed) || stationarySpeed < 0
            || startSpeed <= stationarySpeed || stopSeconds < 1 || maxGapSeconds < stopSeconds)
            throw new IllegalArgumentException("Invalid automatic trip policy");
    }

    public void record(Vehicle vehicle, TrackerLocation fix) {
        var at = fix.getRecordedAt();
        var prior = vehicle.getCurrentLocation();
        // Lazy rollout initialization avoids a fleet-wide data backfill or inventing old trip membership.
        if (vehicle.getTripLastRecordedAt() == null && prior != null && prior.getRecordedAt() != null
            && !prior.getRecordedAt().isAfter(clock.instant())) vehicle.setTripLastRecordedAt(prior.getRecordedAt());
        if (!fix.isValid() || fix.getSpeedKmh() == null || !Float.isFinite(fix.getSpeedKmh())
            || fix.getSpeedKmh() < 0 || at.isAfter(clock.instant())
            || vehicle.getTripLastRecordedAt() != null && !at.isAfter(vehicle.getTripLastRecordedAt())) return;

        Trip trip = trips.findByVehicleVehicleIdAndIsActiveAndOrganizationId(vehicle.getVehicleId(), true,
            vehicle.getOrganizationId()).orElse(null);
        TripPoint last = trip == null ? null : points.findFirstByTripTripIdAndTripVehicleOrganizationIdOrderByPointOrderDesc(
            trip.getTripId(), vehicle.getOrganizationId()).orElse(null);
        if (trip != null && at.isBefore(trip.getStartTime())) return;
        if (last != null && Duration.between(last.getLocation().getRecordedAt(), at).getSeconds() > maxGapSeconds) {
            trip.endTrip(TripEndReason.TIMEOUT, last.getLocation());
            trips.save(trip);
            trip = null; last = null;
        }
        vehicle.setTripLastRecordedAt(at);
        if (trip == null) {
            if (fix.getSpeedKmh() < startSpeed) return;
            trip = new Trip();
            trip.setTripId("AUTO_" + UUID.randomUUID());
            trip.setVehicle(vehicle);
            trip.setStartTime(at);
            trip.setCreatedAt(clock.instant());
            trip.setStartReason(TripStartReason.AUTO_DETECTED);
            trip.setStartLocation(fix);
            trip.setRecordedPointsAssociated(true);
            trip = trips.save(trip);
        }
        // Legacy active trips retain their labelled time-window history semantics.
        TripPoint point = trip.recordLocation(fix, last == null ? null : last.getLocation(), last == null ? 1 : last.getPointOrder() + 1);
        point.setCreatedAt(clock.instant());
        points.save(point);
        if (fix.getSpeedKmh() <= stationarySpeed) {
            if (trip.getStationarySince() == null) trip.setStationarySince(at);
            if (Duration.between(trip.getStationarySince(), at).getSeconds() >= stopSeconds)
                trip.endTrip(TripEndReason.TIMEOUT, fix);
        } else trip.setStationarySince(null);
        trips.save(trip);
    }
}
