package com.surf.tracker.core.domain;

import com.surf.tracker.core.domain.entity.TrackerLocation;
import com.surf.tracker.core.domain.entity.Trip;
import com.surf.tracker.core.domain.entity.Vehicle;
import com.surf.tracker.core.domain.enums.TripEndReason;
import com.surf.tracker.core.domain.enums.TripStartReason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TripTest {

    private Instant testTime;
    private Vehicle vehicle;

    @BeforeEach
    void setUp() {
        testTime = Instant.parse("2026-01-01T12:00:00Z");
        vehicle = new Vehicle();
        vehicle.setVehicleId("VEH-001");
    }

    private TrackerLocation makeLocation(double lat, double lon, float speed, Instant when) {
        TrackerLocation loc = new TrackerLocation();
        loc.setLatitude(lat);
        loc.setLongitude(lon);
        loc.setSpeedKmh(speed);
        loc.setRecordedAt(when);
        return loc;
    }

    private Trip makeTrip(String vehicleId, TrackerLocation startLoc, Instant start) {
        Trip trip = new Trip();
        trip.setTripId("TRIP_" + vehicleId + "_" + UUID.randomUUID().toString().substring(0, 8));
        trip.setVehicle(vehicle);
        trip.setStartLocation(startLoc);
        trip.setStartTime(start);
        trip.setStartReason(TripStartReason.AUTO_DETECTED);
        trip.setIsActive(true);
        trip.setTotalDistanceKm(0.0f);
        return trip;
    }

    @Test
    void activeTrip_getDuration_returnsElapsedTime() {
        TrackerLocation startLoc = makeLocation(40.7128, -74.0060, 0.0f, testTime);
        Trip trip = makeTrip("VEH-001", startLoc, testTime);

        Duration duration = trip.getDuration();

        assertThat(duration.toMillis()).isGreaterThanOrEqualTo(0);
        assertThat(trip.getIsActive()).isTrue();
    }

    @Test
    void endedTrip_getDuration_returnsFixedInterval() {
        TrackerLocation startLoc = makeLocation(40.7128, -74.0060, 0.0f, testTime);
        TrackerLocation endLoc = makeLocation(40.6892, -74.0445, 0.0f, testTime.plusSeconds(300));
        Trip trip = makeTrip("VEH-001", startLoc, testTime);
        trip.setEndTime(testTime.plusSeconds(300));
        trip.setEndLocation(endLoc);
        trip.setEndReason(TripEndReason.ACC_OFF);
        trip.setIsActive(false);

        Duration duration = trip.getDuration();

        assertThat(duration.getSeconds()).isEqualTo(300);
    }

    @Test
    void addLocationPoint_incrementsDistanceAndTrackPoints() {
        TrackerLocation startLoc = makeLocation(40.7128, -74.0060, 30.0f, testTime);
        Trip trip = makeTrip("VEH-001", startLoc, testTime);

        trip.addLocationPoint(startLoc, 0.0f);

        assertThat(trip.getTripPoints()).hasSize(1);
        assertThat(trip.getTotalDistanceKm()).isEqualTo(0.0f);
    }

    @Test
    void addLocationPoint_tracksMaxSpeed() {
        TrackerLocation startLoc = makeLocation(40.7128, -74.0060, 30.0f, testTime);
        TrackerLocation loc2 = makeLocation(40.7589, -73.9851, 80.0f, testTime.plusSeconds(60));
        Trip trip = makeTrip("VEH-001", startLoc, testTime);

        trip.addLocationPoint(startLoc, 0.0f);
        trip.addLocationPoint(loc2, 5.0f);

        assertThat(trip.getMaxSpeedKmh()).isEqualTo(80.0f);
    }

    @Test
    void endTrip_setsEndTimeAndMarksInactive() {
        TrackerLocation startLoc = makeLocation(40.7128, -74.0060, 0.0f, testTime);
        TrackerLocation endLoc = makeLocation(40.6892, -74.0445, 0.0f, testTime.plusSeconds(600));
        Trip trip = makeTrip("VEH-001", startLoc, testTime);

        trip.endTrip(TripEndReason.ACC_OFF, endLoc);

        assertThat(trip.getIsActive()).isFalse();
        assertThat(trip.getEndTime()).isEqualTo(endLoc.getRecordedAt());
        assertThat(trip.getEndReason()).isEqualTo(TripEndReason.ACC_OFF);
        assertThat(trip.getEndLocation()).isEqualTo(endLoc);
    }

    @Test
    void haversineDistance_knownPoints() {
        TrackerLocation nyc = makeLocation(40.7128, -74.0060, 0.0f, testTime);
        TrackerLocation la = makeLocation(34.0522, -118.2437, 0.0f, testTime);

        float distance = tripHaversine(nyc, la);

        assertThat(distance).isGreaterThan(3900.0f);
        assertThat(distance).isLessThan(4000.0f);
    }

    @Test
    void haversineDistance_samePoint_returnsZero() {
        TrackerLocation loc = makeLocation(40.7128, -74.0060, 0.0f, testTime);

        float distance = tripHaversine(loc, loc);

        assertThat(distance).isEqualTo(0.0f);
    }

    @Test
    void calculateStatistics_setsAverageSpeed() {
        TrackerLocation startLoc = makeLocation(40.7128, -74.0060, 0.0f, testTime);
        Trip trip = makeTrip("VEH-001", startLoc, testTime);
        trip.setTotalDistanceKm(120.0f);
        trip.setEndTime(testTime.plusSeconds(7200));

        trip.calculateStatistics();

        assertThat(trip.getAverageSpeedKmh()).isNotNull();
        assertThat(trip.getAverageSpeedKmh()).isEqualTo(60.0f);
    }

    @Test
    void calculateDistance_nullLocations_returnsZero() {
        Trip trip = makeTrip("VEH-001", null, testTime);

        Float d1 = trip.calculateDistance(null, null);
        Float d2 = trip.calculateDistance(null, makeLocation(40.7128, -74.0060, 0.0f, testTime));
        Float d3 = trip.calculateDistance(makeLocation(40.7128, -74.0060, 0.0f, testTime), null);

        assertThat(d1).isEqualTo(0.0f);
        assertThat(d2).isEqualTo(0.0f);
        assertThat(d3).isEqualTo(0.0f);
    }

    @Test
    void calculateDistance_shortDistance() {
        TrackerLocation loc1 = makeLocation(40.7128, -74.0060, 0.0f, testTime);
        TrackerLocation loc2 = makeLocation(40.7130, -74.0062, 0.0f, testTime);
        Trip trip = makeTrip("VEH-001", loc1, testTime);

        Float distance = trip.calculateDistance(loc1, loc2);

        assertThat(distance).isGreaterThan(0.0f);
        assertThat(distance).isLessThan(0.1f);
    }

    private float tripHaversine(TrackerLocation l1, TrackerLocation l2) {
        return new Trip().calculateDistance(l1, l2);
    }
}
