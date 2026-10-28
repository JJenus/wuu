package com.surf.tracker.core.application;

import com.surf.tracker.core.application.service.VehicleService;
import com.surf.tracker.core.domain.entity.Tracker;
import com.surf.tracker.core.domain.entity.Vehicle;
import com.surf.tracker.core.infrastructure.repository.TrackerRepository;
import com.surf.tracker.shared.domain.LocationPoint;
import com.surf.tracker.shared.events.LocationDataEvent;
import com.surf.tracker.shared.events.VehicleUpdatedEvent;
import com.surf.tracker.shared.metrics.MetricsRegistry;
import com.surf.tracker.shared.pubsub.EventPublisher;
import com.surf.tracker.shared.redis.VehicleActivityTracker;
import com.surf.tracker.shared.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VehicleEventHandlerTest {
    @Mock VehicleService vehicleService;
    @Mock TrackerRepository trackerRepository;
    @Mock EventPublisher publisher;
    @Mock VehicleActivityTracker activityTracker;
    @Mock MetricsRegistry metrics;
    @org.mockito.Spy Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    @InjectMocks VehicleEventHandler handler;

    @AfterEach
    void clear_context() { TenantContext.clear(); }

    @Test
    void handleLocationUpdate_no_http_context_uses_persisted_owner() {
        // given
        Tracker tracker = assignedTracker();
        when(trackerRepository.findForTelemetryByDeviceId("device-1")).thenReturn(Optional.of(tracker));
        LocationDataEvent event = event();
        doAnswer(invocation -> {
            assertThat(TenantContext.getCurrentOrgId()).isEqualTo(11L);
            assertThat(TenantContext.getCurrentUserId()).isNull();
            return null;
        }).when(vehicleService).updateVehicleLocation("vehicle-1", event.getDeviceId(), event.getLocation());
        // when
        handler.handleLocationUpdate(event);
        // then
        verify(publisher).publish(argThat(e -> e instanceof VehicleUpdatedEvent update
                && update.getOrganizationId().equals(11L) && update.getVehicleId().equals("vehicle-1")));
        assertThat(TenantContext.getCurrentOrgId()).isNull();
        verify(vehicleService, never()).findVehicleIdForDevice(any());
    }

    @Test
    void handleLocationUpdate_failure_restores_previous_context() {
        // given
        TenantContext.setCurrentOrgId(99L);
        TenantContext.setCurrentUserId(88L);
        when(trackerRepository.findForTelemetryByDeviceId("device-1")).thenReturn(Optional.of(assignedTracker()));
        doThrow(new RuntimeException("database unavailable")).when(vehicleService).updateVehicleLocation(any(), any(), any());
        // when / then
        assertThatThrownBy(() -> handler.handleLocationUpdate(event())).hasMessage("database unavailable");
        assertThat(TenantContext.getCurrentOrgId()).isEqualTo(99L);
        assertThat(TenantContext.getCurrentUserId()).isEqualTo(88L);
        verifyNoInteractions(publisher);
    }

    @Test
    void handleLocationUpdate_unknown_device_does_not_mutate_vehicle() {
        // given
        when(trackerRepository.findForTelemetryByDeviceId("device-1")).thenReturn(Optional.empty());
        // when
        handler.handleLocationUpdate(event());
        // then
        verifyNoInteractions(vehicleService, publisher, activityTracker);
        assertThat(TenantContext.getCurrentOrgId()).isNull();
    }

    private Tracker assignedTracker() {
        Vehicle vehicle = new Vehicle();
        vehicle.setVehicleId("vehicle-1");
        vehicle.setOrganizationId(11L);
        Tracker tracker = new Tracker();
        tracker.setDeviceId("device-1");
        tracker.setVehicle(vehicle);
        return tracker;
    }

    private LocationDataEvent event() {
        return new LocationDataEvent(clock, "device-1", new LocationPoint(1, 2, 0, clock.instant()), "GT06");
    }
}
