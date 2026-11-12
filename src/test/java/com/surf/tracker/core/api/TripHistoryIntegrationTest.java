package com.surf.tracker.core.api;

import com.surf.tracker.core.application.TripQueryService;
import com.surf.tracker.core.domain.entity.*;
import com.surf.tracker.core.infrastructure.repository.VehicleRepository;
import com.surf.tracker.shared.security.TenantContext;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.time.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:trip_history;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.hibernate.naming.physical-strategy=org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = TripHistoryIntegrationTest.Config.class)
class TripHistoryIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-11-12T12:00:00Z");
    @Autowired EntityManager em;
    @Autowired TripQueryService service;
    MockMvc mvc;

    @Configuration
    @EnableJpaRepositories(basePackageClasses = VehicleRepository.class)
    @Import(TripQueryService.class)
    static class Config {
        @Bean Clock clock() { return Clock.fixed(NOW, ZoneOffset.UTC); }
        @Bean PersistenceManagedTypes persistenceManagedTypes() {
            return PersistenceManagedTypes.of(Vehicle.class.getName(), Tracker.class.getName(),
                    Trip.class.getName(), TrackerLocation.class.getName(), TripPoint.class.getName(),
                    TrackerRawData.class.getName(), TrackerAssignment.class.getName(), DeviceCommand.class.getName());
        }
    }

    @BeforeEach void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new TripController(service)).build();
        TenantContext.setCurrentOrgId(1L);
        Vehicle vehicle = new Vehicle();
        vehicle.setVehicleId("owned");
        vehicle.setOrganizationId(1L);
        em.persist(vehicle);
        Trip trip = new Trip();
        trip.setTripId("journey");
        trip.setVehicle(vehicle);
        trip.setStartTime(NOW.minusSeconds(600));
        em.persist(trip);
        Tracker tracker = new Tracker();
        tracker.setTrackerId("gps");
        tracker.setVehicle(vehicle);
        em.persist(tracker);
        // Out-of-order arrival and boundary points, without fabricated trip links.
        point(tracker, NOW.minusSeconds(10), 12.0);
        point(tracker, NOW.minusSeconds(600), 11.0);
        point(tracker, NOW.minusSeconds(601), 10.0);
        point(tracker, NOW.plusSeconds(1), 13.0);
        em.flush();
        em.clear();
    }

    private void point(Tracker tracker, Instant at, double latitude) {
        TrackerLocation point = new TrackerLocation(latitude, 20.0, 42f, at);
        point.setTracker(tracker);
        em.persist(point);
    }

    @AfterEach void clear() { TenantContext.clear(); }

    @Test void history_persistedVehicleWindow_returnsOrderedActualSamples() throws Exception {
        // when / then
        mvc.perform(get("/api/v1/trips/journey/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("VEHICLE_TIME_WINDOW"))
                .andExpect(jsonPath("$.points.length()").value(2))
                .andExpect(jsonPath("$.points[0].latitude").value(11.0))
                .andExpect(jsonPath("$.points[1].latitude").value(12.0))
                .andExpect(jsonPath("$.points[0].speedKmh").value(42.0))
                .andExpect(jsonPath("$.truncated").value(false));
    }

    @Test void history_foreignAndMissingTrip_areIndistinguishable() throws Exception {
        TenantContext.setCurrentOrgId(2L);
        mvc.perform(get("/api/v1/trips/journey/history")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/trips/missing/history")).andExpect(status().isNotFound());
    }

    @Test void history_withoutTenant_failsClosed() throws Exception {
        TenantContext.clear();
        mvc.perform(get("/api/v1/trips/journey/history")).andExpect(status().isForbidden());
    }

    @Test void history_completedEmptyWindow_doesNotInventRoute() throws Exception {
        Trip trip = em.find(Trip.class, "journey");
        trip.setStartTime(NOW.minusSeconds(100));
        trip.setEndTime(NOW.minusSeconds(50));
        em.flush();
        em.clear();
        mvc.perform(get("/api/v1/trips/journey/history"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.points").isEmpty());
    }

    @Test void history_limit_isBoundedAndTruncationIsExplicit() throws Exception {
        mvc.perform(get("/api/v1/trips/journey/history").param("limit", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.points.length()").value(1))
                .andExpect(jsonPath("$.truncated").value(true));
        for (String limit : new String[]{"0", "10001", "-1"})
            mvc.perform(get("/api/v1/trips/journey/history").param("limit", limit))
                    .andExpect(status().isBadRequest());
    }

    @Test void history_foreignVehicleAndInvalidCoordinates_areNeverReturned() throws Exception {
        Vehicle foreign = new Vehicle();
        foreign.setVehicleId("foreign");
        foreign.setOrganizationId(2L);
        em.persist(foreign);
        Tracker tracker = new Tracker();
        tracker.setTrackerId("foreign-gps");
        tracker.setVehicle(foreign);
        em.persist(tracker);
        point(tracker, NOW.minusSeconds(10), 55.0);
        point(em.find(Tracker.class, "gps"), NOW.minusSeconds(5), 91.0);
        em.flush();
        em.clear();
        mvc.perform(get("/api/v1/trips/journey/history"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.points.length()").value(2));
    }
}
