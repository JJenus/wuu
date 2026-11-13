package com.surf.tracker.core.api;

import com.surf.tracker.core.application.service.TrackerService;
import com.surf.tracker.core.application.service.VehicleService;
import com.surf.tracker.core.domain.entity.*;
import com.surf.tracker.shared.security.TenantContext;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@DataJpaTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:fleet_management;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
    "spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate",
    "spring.jpa.hibernate.naming.physical-strategy=org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = CreateIsolationIntegrationTest.Config.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class FleetManagementIntegrationTest {
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired TrackerService trackers;
    @Autowired VehicleService vehicles;
    MockMvc mvc;
    String source, target, foreign, tracker;

    @BeforeEach void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        source = "source-" + suffix; target = "target-" + suffix;
        foreign = "foreign-" + suffix; tracker = "tracker-" + suffix;
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            Vehicle origin = vehicle(source, 1L);
            vehicle(target, 1L); vehicle(foreign, 2L);
            Tracker device = new Tracker(); device.setTrackerId(tracker);
            device.setDeviceId("device-" + suffix); device.setVehicle(origin);
            device.setBatteryLevel(73f); device.setSignalStrength(4); device.setModel("Original");
            em.persist(device);
        });
        TenantContext.setCurrentOrgId(1L);
        mvc = com.surf.tracker.main.api.ApiMockMvc.standaloneSetup(
            new TrackerController(trackers, Clock.systemUTC()), new VehicleController(null, vehicles)).build();
    }

    @AfterEach void clear() { TenantContext.clear(); }

    @Test void reassign_unusedTracker_movesWithinTenantAndKeepsDeviceIdentity() throws Exception {
        assertThat(move(source, target)).isEqualTo(200);
        assertThat(assignedVehicle()).isEqualTo(target);
        assertThat(jdbc.queryForObject("select owner_id from device_identity where device_id=(select device_id from trackers where tracker_id=?)", String.class, tracker)).isEqualTo(tracker);
        // A stale editor cannot move it again using the old assignment.
        assertThat(move(source, source)).isEqualTo(409);
        assertThat(assignedVehicle()).isEqualTo(target);
    }

    @Test void reassign_foreignOrMissingDestination_returnsSame404AndPreservesAssignment() throws Exception {
        assertThat(move(source, foreign)).isEqualTo(404);
        assertThat(move(source, "missing")).isEqualTo(404);
        assertThat(assignedVehicle()).isEqualTo(source);
        TenantContext.setCurrentOrgId(2L);
        assertThat(move(source, foreign)).isEqualTo(404);
    }

    @Test void reassign_missingTenant_failsClosed() throws Exception {
        TenantContext.clear();
        assertThat(move(source, target)).isEqualTo(403);
        assertThat(assignedVehicle()).isEqualTo(source);
    }

    @ParameterizedTest @ValueSource(strings = {"location", "raw", "seen", "command", "assignment", "online"})
    void reassign_usedTracker_rejectsWithoutMovingHistory(String evidence) throws Exception {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            Tracker device = em.find(Tracker.class, tracker);
            switch (evidence) {
                case "location" -> {
                    TrackerLocation fix = new TrackerLocation(51.5, -0.1, 0f, Instant.parse("2026-01-01T12:00:00Z"));
                    fix.setTracker(device); em.persist(fix);
                }
                case "raw" -> {
                    TrackerRawData raw = new TrackerRawData(); raw.setTracker(device); raw.setRawMessage("synthetic"); em.persist(raw);
                }
                case "seen" -> device.setLastSeen(Instant.parse("2026-01-01T12:00:00Z"));
                case "online" -> device.setIsOnline(true);
                case "assignment" -> {
                    TrackerAssignment assignment = new TrackerAssignment(); assignment.setTracker(device);
                    assignment.setVehicle(device.getVehicle()); em.persist(assignment);
                }
                case "command" -> jdbc.update("insert into device_commands(tracker_id,command_type,command_data) values (?,?,'synthetic')", tracker, "READ_STATUS");
            }
        });
        assertThat(move(source, target)).isEqualTo(409);
        assertThat(assignedVehicle()).isEqualTo(source);
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void reassign_eitherVehicleHasActiveTrip_rejects(boolean origin) throws Exception {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            Trip trip = new Trip(); trip.setTripId("trip-" + tracker);
            trip.setVehicle(em.find(Vehicle.class, origin ? source : target));
            trip.setStartTime(Instant.parse("2026-01-01T12:00:00Z")); em.persist(trip);
        });
        assertThat(move(source, target)).isEqualTo(409);
        assertThat(assignedVehicle()).isEqualTo(source);
    }

    @Test void reassign_concurrentEditors_onlyOneExpectedSourceCanWin() throws Exception {
        String third = "third-" + tracker;
        new TransactionTemplate(transactions).executeWithoutResult(status -> vehicle(third, 1L));
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> concurrentMove(start, target));
            var second = executor.submit(() -> concurrentMove(start, third));
            start.countDown();
            assertThat(java.util.List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder(200, 409);
        }
    }

    @Test void update_trackerProfile_preservesTelemetryAndAssignment() throws Exception {
        var response = mvc.perform(patch("/api/v1/trackers/" + tracker + "/profile")
            .contentType(MediaType.APPLICATION_JSON).content("""
            {"model":"Updated","protocol":"GT06","firmwareVersion":"2","simNumber":"555"}
            """)).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(jdbc.queryForObject("select model from trackers where tracker_id=?", String.class, tracker)).isEqualTo("Updated");
        assertThat(jdbc.queryForObject("select battery_level from trackers where tracker_id=?", Float.class, tracker)).isEqualTo(73f);
        assertThat(assignedVehicle()).isEqualTo(source);
        TenantContext.setCurrentOrgId(2L);
        assertThat(mvc.perform(patch("/api/v1/trackers/" + tracker + "/profile").contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn().getResponse().getStatus()).isEqualTo(404);
    }

    @Test void update_vehicleProfile_preservesCurrentPositionAndTripCursor() throws Exception {
        jdbc.update("update vehicles set trip_last_recorded_at=? where vehicle_id=?", java.sql.Timestamp.from(Instant.parse("2026-01-01T12:00:00Z")), source);
        assertThat(mvc.perform(put("/api/v1/vehicles/" + source).contentType(MediaType.APPLICATION_JSON)
            .content("{\"model\":\"Updated van\",\"licensePlate\":\"NEW\",\"vin\":\"123\"}")).andReturn().getResponse().getStatus()).isEqualTo(200);
        assertThat(jdbc.queryForObject("select model from vehicles where vehicle_id=?", String.class, source)).isEqualTo("Updated van");
        assertThat(jdbc.queryForObject("select trip_last_recorded_at from vehicles where vehicle_id=?", java.time.OffsetDateTime.class, source).toInstant()).isEqualTo(Instant.parse("2026-01-01T12:00:00Z"));
    }

    @Test void reassign_inFlightRecordedFix_cannotRacePastHistoryGuard() throws Exception {
        CountDownLatch recorded = new CountDownLatch(1), release = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var ingestion = executor.submit(() -> {
                new TransactionTemplate(transactions).executeWithoutResult(status -> {
                    // Same tracker lock order as VehicleEventHandler, held through location commit.
                    var device = em.createQuery("select t from Tracker t where t.trackerId=:id", Tracker.class)
                        .setParameter("id", tracker).setLockMode(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE).getSingleResult();
                    var fix = new TrackerLocation(51.5, -0.1, 0f, Instant.parse("2026-01-01T12:00:00Z"));
                    fix.setTracker(device); em.persist(fix); em.flush(); recorded.countDown();
                    try { if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Test release timed out"); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
                });
                return true;
            });
            assertThat(recorded.await(10, TimeUnit.SECONDS)).isTrue();
            CountDownLatch moving = new CountDownLatch(1);
            var correction = executor.submit(() -> {
                TenantContext.setCurrentOrgId(1L);
                try { moving.countDown(); return move(source, target); }
                finally { TenantContext.clear(); }
            });
            assertThat(moving.await(10, TimeUnit.SECONDS)).isTrue(); release.countDown();
            assertThat(ingestion.get(10, TimeUnit.SECONDS)).isTrue();
            assertThat(correction.get(10, TimeUnit.SECONDS)).isEqualTo(409);
            assertThat(assignedVehicle()).isEqualTo(source);
        } finally { release.countDown(); }
    }

    @Test void update_invalidTrackerMetadata_rejectsWithoutClearingSavedModel() throws Exception {
        assertThat(mvc.perform(patch("/api/v1/trackers/" + tracker + "/profile").contentType(MediaType.APPLICATION_JSON)
            .content("{\"protocol\":\"" + "x".repeat(21) + "\"}")).andReturn().getResponse().getStatus()).isEqualTo(400);
        assertThat(jdbc.queryForObject("select model from trackers where tracker_id=?", String.class, tracker)).isEqualTo("Original");
    }

    @Test void reassign_blankDestination_rejectsBeforeMutation() throws Exception {
        assertThat(move(source, " ")).isEqualTo(400);
        assertThat(assignedVehicle()).isEqualTo(source);
    }

    private int concurrentMove(CountDownLatch start, String destination) throws Exception {
        TenantContext.setCurrentOrgId(1L);
        try { start.await(); return move(source, destination); }
        finally { TenantContext.clear(); }
    }
    private int move(String expected, String destination) throws Exception {
        return mvc.perform(post("/api/v1/trackers/" + tracker + "/reassign").contentType(MediaType.APPLICATION_JSON)
            .content("{\"sourceVehicleId\":\"" + expected + "\",\"targetVehicleId\":\"" + destination + "\"}"))
            .andReturn().getResponse().getStatus();
    }
    private String assignedVehicle() { return jdbc.queryForObject("select vehicle_id from trackers where tracker_id=?", String.class, tracker); }
    private Vehicle vehicle(String id, long org) {
        Vehicle vehicle = new Vehicle(); vehicle.setVehicleId(id); vehicle.setOrganizationId(org); em.persist(vehicle); return vehicle;
    }
}
