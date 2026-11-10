package com.surf.tracker.core.api;

import com.surf.tracker.core.application.service.TrackerService;
import com.surf.tracker.core.application.service.VehicleService;
import com.surf.tracker.core.domain.entity.Tracker;
import com.surf.tracker.core.domain.entity.TrackerLocation;
import com.surf.tracker.core.domain.entity.Trip;
import com.surf.tracker.core.domain.entity.Vehicle;
import com.surf.tracker.core.infrastructure.repository.VehicleRepository;
import com.surf.tracker.shared.security.TenantContext;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:create_isolation;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.hibernate.naming.physical-strategy=org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl"})
@org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase(
        replace = org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = CreateIsolationIntegrationTest.Config.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CreateIsolationIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");
    @Autowired private VehicleService vehicleService;
    @Autowired private TrackerService trackerService;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;
    private MockMvc mvc;

    @Configuration
    @EnableJpaRepositories(basePackageClasses = VehicleRepository.class)
    @Import({VehicleService.class, TrackerService.class})
    static class Config {
        @Bean
        PersistenceManagedTypes persistenceManagedTypes() {
            return PersistenceManagedTypes.of(Vehicle.class.getName(), Tracker.class.getName(),
                    Trip.class.getName(), TrackerLocation.class.getName(),
                    "com.surf.tracker.core.domain.entity.TripPoint",
                    "com.surf.tracker.core.domain.entity.TrackerRawData",
                    "com.surf.tracker.core.domain.entity.TrackerAssignment",
                    "com.surf.tracker.core.domain.entity.DeviceCommand");
        }
    }

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new VehicleController(null, vehicleService),
                new TrackerController(trackerService, Clock.fixed(NOW, ZoneOffset.UTC))).build();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Vehicle original = vehicle("original", "vehicle-device", 1L);
            Tracker tracker = new Tracker();
            tracker.setTrackerId("original-tracker");
            tracker.setDeviceId("tracker-device");
            tracker.setModel("original-model");
            original.addTracker(tracker);
            TrackerLocation location = new TrackerLocation(1.0, 2.0, 3.0f, NOW);
            tracker.addLocation(location);
            original.setCurrentLocation(location);
            Trip trip = new Trip();
            trip.setTripId("original-trip");
            trip.setStartTime(NOW);
            original.addTrip(trip);
            entityManager.persist(original);
            entityManager.persist(vehicle("target", "target-device", 2L));
        });
        TenantContext.setCurrentOrgId(2L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbc.update("update vehicles set current_location_id = null");
            jdbc.update("delete from trips");
            jdbc.update("delete from tracker_location_data");
            jdbc.update("delete from trackers");
            jdbc.update("delete from vehicles");
        });
    }

    @ParameterizedTest
    @ValueSource(longs = {1L, 2L})
    void createVehicle_existingId_returnsConflictAndPreservesGraph(long callerOrg) throws Exception {
        // given
        TenantContext.setCurrentOrgId(callerOrg);
        var before = snapshot();
        // when
        var response = mvc.perform(post("/api/v1/vehicles").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"vehicleId":"original","deviceId":"replacement-device","model":"replacement"}
                        """)).andReturn().getResponse();
        // then
        assertThat(snapshot()).isEqualTo(before);
        assertThat(response.getStatus()).isEqualTo(409);
    }

    @ParameterizedTest
    @ValueSource(longs = {1L, 2L})
    void createTracker_existingId_returnsConflictAndPreservesGraph(long callerOrg) throws Exception {
        // given
        TenantContext.setCurrentOrgId(callerOrg);
        String target = callerOrg == 1L ? "original" : "target";
        var before = snapshot();
        // when
        var response = mvc.perform(post("/api/v1/trackers").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"trackerId":"original-tracker","deviceId":"replacement-device",
                         "vehicleId":"%s","model":"replacement"}
                        """.formatted(target))).andReturn().getResponse();
        // then
        assertThat(snapshot()).isEqualTo(before);
        assertThat(response.getStatus()).isEqualTo(409);
    }

    @ParameterizedTest
    @ValueSource(strings = {"vehicles", "trackers"})
    void create_existingDeviceId_returnsConflictAndPreservesGraph(String resource) throws Exception {
        // given
        var before = snapshot();
        String body = resource.equals("vehicles")
                ? "{\"vehicleId\":\"new\",\"deviceId\":\"vehicle-device\"}"
                : "{\"trackerId\":\"new\",\"deviceId\":\"tracker-device\",\"vehicleId\":\"target\"}";
        // when
        var response = mvc.perform(post("/api/v1/" + resource).contentType(MediaType.APPLICATION_JSON)
                .content(body)).andReturn().getResponse();
        // then
        assertThat(snapshot()).isEqualTo(before);
        assertThat(response.getStatus()).isEqualTo(409);
    }

    @Test
    void create_newIds_returnsCreatedAndScopesOwnership() throws Exception {
        // given / when
        var vehicle = mvc.perform(post("/api/v1/vehicles").contentType(MediaType.APPLICATION_JSON)
                .content("{\"vehicleId\":\"new\",\"deviceId\":\"new-vehicle-device\"}"))
                .andReturn().getResponse();
        var tracker = mvc.perform(post("/api/v1/trackers").contentType(MediaType.APPLICATION_JSON)
                .content("{\"trackerId\":\"new\",\"deviceId\":\"new-tracker-device\",\"vehicleId\":\"new\"}"))
                .andReturn().getResponse();
        // then
        assertThat(vehicle.getStatus()).isEqualTo(201);
        assertThat(tracker.getStatus()).isEqualTo(201);
        assertThat(jdbc.queryForObject("select organization_id from vehicles where vehicle_id='new'", Long.class))
                .isEqualTo(2L);
        assertThat(jdbc.queryForObject("select vehicle_id from trackers where tracker_id='new'", String.class))
                .isEqualTo("new");
    }

    @Test
    void update_authorizedVehicle_changesFieldsWithoutReplacingAssociations() throws Exception {
        // given
        TenantContext.setCurrentOrgId(1L);
        // when
        var response = mvc.perform(put("/api/v1/vehicles/original").contentType(MediaType.APPLICATION_JSON)
                .content("{\"model\":\"updated\"}")).andReturn().getResponse();
        // then
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(jdbc.queryForObject("select model from vehicles where vehicle_id='original'", String.class))
                .isEqualTo("updated");
        assertThat(jdbc.queryForObject("select count(*) from trackers where vehicle_id='original'", Long.class))
                .isEqualTo(1L);
        assertThat(jdbc.queryForObject("select count(*) from trips where vehicle_id='original'", Long.class))
                .isEqualTo(1L);
    }

    private Map<String, List<Map<String, Object>>> snapshot() {
        return Map.of("vehicles", jdbc.queryForList("select * from vehicles order by vehicle_id"),
                "trackers", jdbc.queryForList("select * from trackers order by tracker_id"),
                "locations", jdbc.queryForList("select * from tracker_location_data order by location_id"),
                "trips", jdbc.queryForList("select * from trips order by trip_id"));
    }

    private Vehicle vehicle(String id, String deviceId, long organizationId) {
        Vehicle vehicle = new Vehicle();
        vehicle.setVehicleId(id);
        vehicle.setDeviceId(deviceId);
        vehicle.setModel("original-model");
        vehicle.setOrganizationId(organizationId);
        return vehicle;
    }
}
