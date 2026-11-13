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
@org.springframework.context.annotation.Import(TelemetryReplayIntegrationTest.TimeConfig.class)
class TelemetryReplayIntegrationTest {
    @org.springframework.boot.test.context.TestConfiguration
    static class TimeConfig {
        @org.springframework.context.annotation.Bean
        @org.springframework.context.annotation.Primary
        Clock fixtureClock() { return Clock.fixed(Instant.parse("2026-11-12T12:00:00Z"), ZoneOffset.UTC); }
    }
    @Autowired EntityManager entities;
    @Autowired PlatformTransactionManager transactions;
    @Autowired VehicleEventHandler handler;
    @Autowired JdbcTemplate jdbc;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean TelemetryReceiptStore receipts;
    @MockitoBean EventPublisher publisher;
    @MockitoBean VehicleActivityTracker activity;
    private static final AtomicInteger IDS = new AtomicInteger();
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-12T12:00:00Z"), ZoneOffset.UTC);

    @org.junit.jupiter.api.BeforeEach
    void clearOwnedFixtureOutbox() {
        // This class owns the isolated telemetry-replay H2 database; no scheduled relay runs here.
        jdbc.update("delete from telemetry_receipts");
    }

    @Test
    void receive_sameGpsFixWithNewSourceId_persistsAndPublishesOnlyOnce() {
        String device = assignedDevice();
        handler.handleLocationUpdate(event(device, 0, 51.5));
        handler.handleLocationUpdate(event(device, 0, 51.5));
        assertThat(locationCount(device)).isEqualTo(1);
        receipts.relayPending();
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
        receipts.relayPending();
        verify(publisher, times(1)).publish(any());
    }

    @Test
    void receive_publicationFailure_retainsIntentAndRetriesWithoutDuplicatingLocation() {
        String device = assignedDevice();
        doThrow(new IllegalStateException("test publication failure")).doNothing().when(publisher).publish(any());
        var source = event(device, 0, 51.5);
        handler.handleLocationUpdate(source);
        receipts.relayPending();
        assertThat(locationCount(device)).isEqualTo(1);
        jdbc.update("update telemetry_receipts set next_attempt_at=received_at where device_id=?", device);
        receipts.relayPending();
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
    void receive_receiptStorageFailure_rollsBackLocationAndAllowsRetry() {
        String device = assignedDevice();
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("test receipt storage unavailable"))
                .doCallRealMethod().when(receipts).record(anyString(), anyLong(), anyString(), any(), any());
        var source = event(device, 0, 51.5);
        assertThatThrownBy(() -> handler.handleLocationUpdate(source))
                .isInstanceOf(org.springframework.dao.DataAccessResourceFailureException.class);
        assertThat(locationCount(device)).isZero();
        assertThat(tripCount(device)).isZero();
        handler.handleLocationUpdate(source);
        handler.handleLocationUpdate(source);
        receipts.relayPending();
        assertThat(locationCount(device)).isEqualTo(1);
        verify(publisher, times(1)).publish(any());
    }

    @Test
    void receive_futureFix_retainsHistoryWithoutPoisoningLivePositionOrAlerts() {
        String device = assignedDevice();
        handler.handleLocationUpdate(event(device, 0, 51.5));
        handler.handleLocationUpdate(event(device, 3600, 55));
        assertThat(locationCount(device)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select l.latitude from vehicles v join trackers t on t.vehicle_id=v.vehicle_id "
                + "join tracker_location_data l on l.location_id=v.current_location_id where t.device_id=?",
                Double.class, device)).isEqualTo(51.5);
        receipts.relayPending();
        verify(publisher, times(1)).publish(any());
    }

    @Test
    void receive_oldFirstFix_retainsLastKnownPositionWithoutHistoricalAlertIntent() {
        String device = assignedDevice();
        handler.handleLocationUpdate(event(device, -3600, 51.5));
        assertThat(locationCount(device)).isEqualTo(1);
        receipts.relayPending();
        verifyNoInteractions(publisher, activity);
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
        receipts.relayPending();
        verify(publisher, times(1)).publish(any());
        assertThat(tripCount(device)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from trip_points where trip_id=?", Integer.class, onlyTrip(device))).isEqualTo(1);
    }

    @Test
    void receive_movementAndConfirmedStop_persistsOneTripAndOrderedAssociatedFixes() {
        String device = assignedDevice();
        handler.handleLocationUpdate(event(device, -600, 51.5));
        handler.handleLocationUpdate(event(device, -540, 51.51));
        handler.handleLocationUpdate(event(device, -530, 51.51, 0));
        handler.handleLocationUpdate(event(device, -230, 51.51, 0));
        String trip = onlyTrip(device);
        assertThat(jdbc.queryForObject("select is_active from trips where trip_id=?", Boolean.class, trip)).isFalse();
        assertThat(jdbc.queryForObject("select end_reason from trips where trip_id=?", String.class, trip)).isEqualTo("TIMEOUT");
        assertThat(jdbc.queryForObject("select start_time from trips where trip_id=?", java.sql.Timestamp.class, trip).toInstant())
                .isEqualTo(clock.instant().minusSeconds(600));
        assertThat(jdbc.queryForObject("select count(*) from tracker_location_data where trip_id=?", Integer.class, trip)).isEqualTo(4);
        assertThat(jdbc.queryForList("select point_order from trip_points where trip_id=? order by point_order", Integer.class, trip))
                .containsExactly(1, 2, 3, 4);
        assertThat(jdbc.queryForObject("select total_distance_km from trips where trip_id=?", Float.class, trip)).isBetween(1.0f, 1.2f);
        assertThat(jdbc.queryForObject("select average_speed_kmh from trips where trip_id=?", Float.class, trip)).isBetween(10f, 12f);
        handler.handleLocationUpdate(event(device, -230, 51.51, 0));
        handler.handleLocationUpdate(event(device, -200, 51.52));
        assertThat(tripCount(device)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from trip_points where trip_id=?", Integer.class, trip)).isEqualTo(4);
    }

    @Test
    void receive_idleBeforeMovementAndTransientStop_doesNotCreateOrPrematurelyEndTrip() {
        String device = assignedDevice();
        handler.handleLocationUpdate(event(device, -500, 51.5, 0));
        handler.handleLocationUpdate(event(device, -490, 51.5, 2));
        assertThat(tripCount(device)).isZero();
        handler.handleLocationUpdate(event(device, -480, 51.5));
        handler.handleLocationUpdate(event(device, -400, 51.5, 0));
        handler.handleLocationUpdate(event(device, -200, 51.51));
        handler.handleLocationUpdate(event(device, -100, 51.51, 0));
        assertThat(jdbc.queryForObject("select is_active from trips where trip_id=?", Boolean.class, onlyTrip(device))).isTrue();
    }

    @Test
    void receive_lateAndFutureFixes_retainHistoryWithoutChangingTripMembership() {
        String device = assignedDevice();
        handler.handleLocationUpdate(event(device, -200, 51.5));
        handler.handleLocationUpdate(event(device, -100, 51.51));
        handler.handleLocationUpdate(event(device, -150, 51.505));
        handler.handleLocationUpdate(event(device, 60, 55));
        handler.handleLocationUpdate(event(device, 0, 51.52));
        assertThat(locationCount(device)).isEqualTo(5);
        assertThat(jdbc.queryForObject("select count(*) from tracker_location_data where trip_id=?", Integer.class, onlyTrip(device))).isEqualTo(3);
    }

    @Test
    void receive_reportingGap_splitsAtLastObservedFixWithoutBridgingDistance() {
        String device = assignedDevice();
        handler.handleLocationUpdate(event(device, -2000, 51.5));
        handler.handleLocationUpdate(event(device, -100, 55));
        assertThat(tripCount(device)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select sum(total_distance_km) from trips where vehicle_id=(select vehicle_id from trackers where device_id=?)", Float.class, device)).isZero();
    }

    private int tripCount(String device) {
        return jdbc.queryForObject("select count(*) from trips where vehicle_id=(select vehicle_id from trackers where device_id=?)", Integer.class, device);
    }

    private String onlyTrip(String device) {
        return jdbc.queryForObject("select trip_id from trips where vehicle_id=(select vehicle_id from trackers where device_id=?)", String.class, device);
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
        return event(device, seconds, latitude, 24);
    }

    private LocationDataEvent event(String device, int seconds, double latitude, float speed) {
        return new LocationDataEvent(clock, UUID.randomUUID(), device,
                new LocationPoint(latitude, -0.1, speed, clock.instant().plusSeconds(seconds)), "AUTOSEEKER", null);
    }

    private int locationCount(String device) {
        return jdbc.queryForObject("select count(*) from tracker_location_data l join trackers t "
                + "on t.tracker_id=l.tracker_id where t.device_id=?", Integer.class, device);
    }
}
