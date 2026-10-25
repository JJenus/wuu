package com.surf.tracker.core.domain;

import com.surf.tracker.core.domain.entity.TrackerLocation;
import com.surf.tracker.core.domain.entity.Trip;
import com.surf.tracker.core.domain.entity.Vehicle;
import com.surf.tracker.core.domain.enums.EngineState;
import com.surf.tracker.core.domain.enums.TripEndReason;
import com.surf.tracker.core.exception.TripException;
import com.surf.tracker.core.exception.VehicleException;
import com.surf.tracker.shared.domain.LocationPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VehicleTest {

    private Vehicle vehicle;
    private Instant testTime;
    private Clock fixedClock;

    @BeforeEach
    void setUp() {
        testTime = Instant.parse("2026-01-01T12:00:00Z");
        fixedClock = Clock.fixed(testTime, ZoneOffset.UTC);
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

    @Test
    void vehicleCreation_defaultsToOff() {
        Vehicle v = new Vehicle();
        v.setVehicleId("VEH-X");
        assertThat(v.getEngineState()).isEqualTo(EngineState.OFF);
        assertThat(v.getFuelCutActive()).isFalse();
    }

    @Test
    void processNewTelemetryWithValidLocation_startsMovingAndCreatesTrip() {
        LocationPoint location = new LocationPoint(40.7128, -74.0060, 60.5f, testTime);
        vehicle.setCurrentLocation(makeLocation(40.7128, -74.0060, 0.0f, testTime));

        vehicle.processNewTelemetry(location, testTime);

        assertThat(vehicle.getEngineState()).isEqualTo(EngineState.MOVING);
        assertThat(vehicle.getActiveTrip()).isNotNull();
        assertThat(vehicle.getActiveTrip().getIsActive()).isTrue();
    }

    @Test
    void processNewTelemetryWithInvalidLocation_throwsException() {
        LocationPoint invalidLocation = new LocationPoint(100.0, -74.0060, 60.5f, testTime);

        assertThatThrownBy(() -> vehicle.processNewTelemetry(invalidLocation, testTime))
            .isInstanceOf(VehicleException.class)
            .satisfies(e -> assertThat(((VehicleException) e).getErrorCode()).isEqualTo("VEHICLE_INVALID_LOCATION"));
    }

    @Test
    void processNewTelemetryStartsTripWhenMoving() {
        vehicle.setCurrentLocation(makeLocation(40.7128, -74.0060, 0.0f, testTime));
        LocationPoint location = new LocationPoint(40.7128, -74.0060, 10.0f, testTime);

        vehicle.processNewTelemetry(location, testTime);

        assertThat(vehicle.getActiveTrip()).isNotNull();
        assertThat(vehicle.getActiveTrip().getVehicle()).isEqualTo(vehicle);
    }

    @Test
    void processNewTelemetryDoesNotStartTripWhenStationary() {
        LocationPoint location = new LocationPoint(40.7128, -74.0060, 0.0f, testTime);

        vehicle.processNewTelemetry(location, testTime);

        assertThat(vehicle.getActiveTrip()).isNull();
        assertThat(vehicle.getEngineState()).isEqualTo(EngineState.OFF);
    }

    @Test
    void issueFuelCutOffCommandWhenStationary() {
        vehicle.setCurrentLocation(makeLocation(40.7128, -74.0060, 0.0f, testTime));

        vehicle.issueFuelCutOffCommand(testTime);

        assertThat(vehicle.getFuelCutActive()).isTrue();
        assertThat(vehicle.getEngineState()).isEqualTo(EngineState.FUEL_CUT_ACTIVE);
    }

    @Test
    void issueFuelCutOffCommandWhenMovingTooFast() {
        vehicle.setCurrentLocation(makeLocation(40.7128, -74.0060, 50.0f, testTime));

        assertThatThrownBy(() -> vehicle.issueFuelCutOffCommand(testTime))
            .isInstanceOf(VehicleException.class)
            .satisfies(e -> {
                VehicleException ve = (VehicleException) e;
                assertThat(ve.getErrorCode()).isEqualTo("VEHICLE_FUEL_CUT_MOVING");
                assertThat(ve.getMessage()).contains("50.0");
            });
        assertThat(vehicle.getFuelCutActive()).isFalse();
    }

    @Test
    void issueFuelCutOffCommandWhenAlreadyCut() {
        vehicle.setCurrentLocation(makeLocation(40.7128, -74.0060, 0.0f, testTime));
        vehicle.issueFuelCutOffCommand(testTime);

        assertThatThrownBy(() -> vehicle.issueFuelCutOffCommand(testTime))
            .isInstanceOf(VehicleException.class)
            .satisfies(e -> assertThat(((VehicleException) e).getErrorCode()).isEqualTo("VEHICLE_FUEL_CUT_ACTIVE"));
    }

    @Test
    void issueFuelRestoreCommandWhenStationary() {
        vehicle.setCurrentLocation(makeLocation(40.7128, -74.0060, 0.0f, testTime));
        vehicle.issueFuelCutOffCommand(testTime);

        vehicle.issueFuelRestoreCommand(testTime);

        assertThat(vehicle.getFuelCutActive()).isFalse();
        assertThat(vehicle.getEngineState()).isEqualTo(EngineState.IDLE);
    }

    @Test
    void endActiveTrip_marksInactive() {
        vehicle.setCurrentLocation(makeLocation(40.7128, -74.0060, 0.0f, testTime));
        LocationPoint moving = new LocationPoint(40.7128, -74.0060, 30.0f, testTime);
        vehicle.processNewTelemetry(moving, testTime);

        Trip active = vehicle.getActiveTrip();
        assertThat(active).isNotNull();

        TrackerLocation endLoc = makeLocation(40.7589, -73.9851, 0.0f, testTime.plusSeconds(300));
        vehicle.endActiveTrip(TripEndReason.ACC_OFF, endLoc);

        assertThat(vehicle.getActiveTrip()).isNull();
    }

    @Test
    void engineStateTransitions() {
        assertThat(vehicle.getEngineState()).isEqualTo(EngineState.OFF);

        vehicle.setCurrentLocation(makeLocation(40.7128, -74.0060, 0.0f, testTime));
        vehicle.processNewTelemetry(new LocationPoint(40.7128, -74.0060, 30.0f, testTime), testTime);
        assertThat(vehicle.getEngineState()).isEqualTo(EngineState.MOVING);

        vehicle.processNewTelemetry(new LocationPoint(40.7128, -74.0060, 0.0f, testTime.plusSeconds(10)), testTime.plusSeconds(10));
        assertThat(vehicle.getEngineState()).isEqualTo(EngineState.IDLE);

        vehicle.issueFuelCutOffCommand(testTime.plusSeconds(15));
        assertThat(vehicle.getEngineState()).isEqualTo(EngineState.FUEL_CUT_ACTIVE);

        vehicle.issueFuelRestoreCommand(testTime.plusSeconds(20));
        assertThat(vehicle.getEngineState()).isEqualTo(EngineState.IDLE);
    }

    @Test
    void getIdleDurationWhenNotIdle_returnsZero() {
        assertThat(vehicle.getIdleDuration(testTime)).isEqualTo(Duration.ZERO);

        vehicle.setCurrentLocation(makeLocation(40.7128, -74.0060, 0.0f, testTime));
        vehicle.processNewTelemetry(new LocationPoint(40.7128, -74.0060, 30.0f, testTime), testTime);

        assertThat(vehicle.getIdleDuration(testTime)).isEqualTo(Duration.ZERO);
    }
}
