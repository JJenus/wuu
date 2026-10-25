package com.surf.tracker.core.application;

import com.surf.tracker.core.domain.entity.TrackerLocation;
import com.surf.tracker.core.domain.entity.Vehicle;
import com.surf.tracker.core.exception.VehicleException;
import com.surf.tracker.core.infrastructure.repository.VehicleRepository;
import com.surf.tracker.shared.pubsub.EventPublisher;
import com.surf.tracker.shared.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VehicleCommandServiceTest {

    private static final Long ORG_ID = 1L;

    @Mock
    private VehicleRepository vehicleRepository;

    @Mock
    private EventPublisher eventPublisher;

    @Captor
    private ArgumentCaptor<Vehicle> vehicleCaptor;

    @Captor
    private ArgumentCaptor<com.surf.tracker.shared.pubsub.DomainEvent> eventCaptor;

    private VehicleCommandService commandService;
    private Vehicle testVehicle;
    private Clock fixedClock;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentOrgId(ORG_ID);
        fixedClock = Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"), ZoneOffset.UTC);
        commandService = new VehicleCommandService(vehicleRepository, eventPublisher, fixedClock);
        testVehicle = new Vehicle();
        testVehicle.setVehicleId("VEH-001");
        testVehicle.setDeviceId("DEV-001");
        testVehicle.setOrganizationId(ORG_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private void setVehicleStationary(Vehicle v) {
        TrackerLocation loc = new TrackerLocation();
        loc.setLatitude(40.7128);
        loc.setLongitude(-74.0060);
        loc.setSpeedKmh(0.0f);
        loc.setRecordedAt(Instant.now(fixedClock));
        v.setCurrentLocation(loc);
    }

    private void setVehicleMoving(Vehicle v, float speed) {
        TrackerLocation loc = new TrackerLocation();
        loc.setLatitude(40.7128);
        loc.setLongitude(-74.0060);
        loc.setSpeedKmh(speed);
        loc.setRecordedAt(Instant.now(fixedClock));
        v.setCurrentLocation(loc);
    }

    @Test
    void handleFuelCutRequestSuccess() {
        setVehicleStationary(testVehicle);

        when(vehicleRepository.findByIdAndOrganizationId("VEH-001", ORG_ID)).thenReturn(Optional.of(testVehicle));

        commandService.handleFuelCutRequest("VEH-001");

        verify(vehicleRepository).save(vehicleCaptor.capture());
        verify(eventPublisher).publish(eventCaptor.capture());

        Vehicle savedVehicle = vehicleCaptor.getValue();
        assertThat(savedVehicle.getFuelCutActive()).isTrue();

        com.surf.tracker.shared.pubsub.DomainEvent publishedEvent = eventCaptor.getValue();
        assertThat(publishedEvent).isNotNull();
    }

    @Test
    void handleFuelCutRequestVehicleNotFound() {
        when(vehicleRepository.findByIdAndOrganizationId("VEH-999", ORG_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> commandService.handleFuelCutRequest("VEH-999"))
            .isInstanceOf(VehicleException.class)
            .satisfies(e -> assertThat(((VehicleException) e).getErrorCode()).isEqualTo("VEHICLE_NOT_FOUND"));

        verify(vehicleRepository, never()).save(any());
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void handleFuelCutRequestWhenMovingTooFast() {
        setVehicleMoving(testVehicle, 50.0f);

        when(vehicleRepository.findByIdAndOrganizationId("VEH-001", ORG_ID)).thenReturn(Optional.of(testVehicle));

        assertThatThrownBy(() -> commandService.handleFuelCutRequest("VEH-001"))
            .isInstanceOf(VehicleException.class)
            .satisfies(e -> assertThat(((VehicleException) e).getErrorCode()).isEqualTo("VEHICLE_FUEL_CUT_MOVING"));

        verify(vehicleRepository, never()).save(any());
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void handleFuelRestoreRequestSuccess() {
        setVehicleStationary(testVehicle);
        testVehicle.setFuelCutActive(true);

        when(vehicleRepository.findByIdAndOrganizationId("VEH-001", ORG_ID)).thenReturn(Optional.of(testVehicle));

        commandService.handleFuelRestoreRequest("VEH-001");

        verify(vehicleRepository).save(vehicleCaptor.capture());

        Vehicle savedVehicle = vehicleCaptor.getValue();
        assertThat(savedVehicle.getFuelCutActive()).isFalse();
    }
}
