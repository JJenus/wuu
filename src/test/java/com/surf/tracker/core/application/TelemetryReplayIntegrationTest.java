package com.surf.tracker.core.application;

import com.surf.tracker.core.domain.entity.Tracker;
import com.surf.tracker.core.domain.entity.Vehicle;
import com.surf.tracker.main.TrackingEngineApplication;
import com.surf.tracker.shared.domain.LocationPoint;
import com.surf.tracker.shared.events.LocationDataEvent;
import com.surf.tracker.shared.pubsub.EventPublisher;
import com.surf.tracker.shared.redis.VehicleActivityTracker;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = TrackingEngineApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:telemetry-replay;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jms.listener.auto-startup=false", "tracking.alert-outbox.poll-delay-ms=3600000",
        "tracking.telemetry-outbox.poll-delay-ms=3600000", "tracking.telemetry-outbox.initial-delay-ms=3600000",
        "userauth.jwt.secret=telemetry-test-only-secret-at-least-thirty-two-bytes"})
class TelemetryReplayIntegrationTest {
    @Autowired EntityManager entities;
    @Autowired PlatformTransactionManager transactions;
    @Autowired VehicleEventHandler handler;
    @Autowired JdbcTemplate jdbc;
    @Autowired TelemetryReceiptStore receipts;
    @MockitoBean EventPublisher publisher;
    @MockitoBean VehicleActivityTracker activity;
    private static final AtomicInteger IDS = new AtomicInteger();
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-12T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void receive_sameGpsFixWithNewSourceId_persistsAndPublishesOnlyOnce() {
        String device = assignedDevice();
        handler.handleLocationUpdate(event(device, 0, 51.5));
        handler.handleLocationUpdate(event(device, 0, 51.5));
        assertThat(locationCount(device)).isEqualTo(1);
        verify(publisher, times(1)).publish(any());
    }

    @Test
    void receive_lateFix_retainsHistoryWithoutRewindingCurrentPositionOrPublishingOldAlert() {
        String device = assignedDevice();
        handler.handleLocationUpdate(event(device, 10, 51.51));
        handler.handleLocationUpdate(event(device, 0, 51.5));
        assertThat(locationCount(device)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select l.latitude from vehicles v join trackers t on t.vehicle_id=v.vehicle_id "
                + "join tracker_location_data l on l.location_id=v.current_location_id where t.device_id=?",
                Double.class, device)).isEqualTo(51.51);
        verify(publisher, times(1)).publish(any());
    }

    @Test
    void receive_publicationFailure_rollsBackReceiptAndLocationSoRetryCanComplete() {
        String device = assignedDevice();
        doThrow(new IllegalStateException("test publication failure")).doNothing().when(publisher).publish(any());
        var source = event(device, 0, 51.5);
        assertThatThrownBy(() -> handler.handleLocationUpdate(source)).isInstanceOf(IllegalStateException.class);
        assertThat(locationCount(device)).isZero();
        handler.handleLocationUpdate(source);
        handler.handleLocationUpdate(source);
        assertThat(locationCount(device)).isEqualTo(1);
        verify(publisher, times(2)).publish(any());
    }

    @Test
    void receive_brokerUnavailable_commitsLocationAndIntentBeforeRelay() {
        String device = assignedDevice();
        doThrow(new IllegalStateException("test broker unavailable")).when(publisher).publish(any());
        assertThatCode(() -> handler.handleLocationUpdate(event(device, 0, 51.5))).doesNotThrowAnyException();
        assertThat(locationCount(device)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from telemetry_receipts where device_id=? and update_payload is not null "
                + "and published_at is null", Integer.class, device)).isEqualTo(1);
    }

    @Test
    void receive_concurrentDuplicateFixes_serializesReceiptAndCreatesOneLocation() throws Exception {
        String device = assignedDevice();
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> { start.await(); handler.handleLocationUpdate(event(device, 0, 51.5)); return true; });
            var second = executor.submit(() -> { start.await(); handler.handleLocationUpdate(event(device, 0, 51.5)); return true; });
            start.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS)).isTrue();
            assertThat(second.get(10, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(locationCount(device)).isEqualTo(1);
        verify(publisher, times(1)).publish(any());
    }

    private String assignedDevice() {
        String suffix = Integer.toString(IDS.incrementAndGet());
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            var vehicle = new Vehicle();
            vehicle.setVehicleId("replay-vehicle-" + suffix); vehicle.setOrganizationId(12L);
            entities.persist(vehicle);
            var tracker = new Tracker();
            tracker.setTrackerId("replay-tracker-" + suffix); tracker.setDeviceId("replay-device-" + suffix);
            tracker.setVehicle(vehicle); entities.persist(tracker);
        });
        return "replay-device-" + suffix;
    }

    private LocationDataEvent event(String device, int seconds, double latitude) {
        return new LocationDataEvent(clock, UUID.randomUUID(), device,
                new LocationPoint(latitude, -0.1, 24, clock.instant().plusSeconds(seconds)), "AUTOSEEKER", null);
    }

    private int locationCount(String device) {
        return jdbc.queryForObject("select count(*) from tracker_location_data l join trackers t "
                + "on t.tracker_id=l.tracker_id where t.device_id=?", Integer.class, device);
    }
}
