package com.surf.tracker.core.api;

import com.surf.tracker.core.application.TripExportService;
import com.surf.tracker.core.domain.entity.*;
import com.surf.tracker.core.infrastructure.repository.TripRepository;
import com.surf.tracker.shared.security.TenantContext;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;

import java.time.*;
import java.util.*;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Runs against migrated H2, or an explicitly supplied disposable PostgreSQL database. */
@DataJpaTest(properties = {
    "spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate",
    "spring.jpa.hibernate.naming.physical-strategy=org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = TripExportIntegrationTest.Config.class)
class TripExportIntegrationTest {
    private static final Instant START = Instant.parse("2026-11-12T10:00:00Z");
    private static final Instant END = START.plusSeconds(3600);
    @Autowired EntityManager em;
    @Autowired TripExportService service;
    @Autowired Clock clock;
    private MockMvc mvc;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        String url = System.getenv("REPORTING_JDBC_URL");
        boolean postgres = url != null;
        properties.add("spring.datasource.url", () -> postgres ? url : "jdbc:h2:mem:trip_export;DB_CLOSE_DELAY=-1;MODE=PostgreSQL");
        properties.add("spring.datasource.username", () -> postgres ? "reporting_test" : "sa");
        properties.add("spring.datasource.password", () -> "");
        properties.add("spring.datasource.driver-class-name", () -> postgres ? "org.postgresql.Driver" : "org.h2.Driver");
        properties.add("spring.flyway.locations", () -> postgres ? "classpath:db/postgresql" : "classpath:db/migration");
        properties.add("spring.flyway.postgresql.transactional-lock", () -> false);
    }

    @Configuration
    @EnableJpaRepositories(basePackageClasses = TripRepository.class)
    @Import(TripExportService.class)
    static class Config {
        @Bean Clock clock() { return Clock.fixed(END, ZoneOffset.UTC); }
        @Bean PersistenceManagedTypes persistenceManagedTypes() {
            return PersistenceManagedTypes.of(Vehicle.class.getName(), Tracker.class.getName(),
                Trip.class.getName(), TrackerLocation.class.getName(), TripPoint.class.getName(),
                TrackerRawData.class.getName(), TrackerAssignment.class.getName(), DeviceCommand.class.getName());
        }
    }

    @BeforeEach void setUp() {
        TenantContext.setCurrentOrgId(1L);
        mvc = com.surf.tracker.main.api.ApiMockMvc.standaloneSetup(new TripExportController(service, clock)).build();
        Vehicle owned = vehicle("owned", 1L);
        trip("before", owned, START.minusSeconds(1));
        trip("at-start", owned, START);
        trip("middle", owned, START.plusSeconds(1));
        trip("at-end", owned, END);
        trip("after", owned, END.plusSeconds(1));
        trip("other-owned", vehicle("other", 1L), START);
        trip("foreign", vehicle("foreign-vehicle", 2L), START);
        em.flush();
        em.clear();
    }

    @AfterEach void clear() { TenantContext.clear(); }

    static Stream<Arguments> filters() {
        return Stream.of(false, true).flatMap(byVehicle -> Stream.of(
            Arguments.of(byVehicle, null, null, List.of("before", "at-start", "middle", "at-end", "after")),
            Arguments.of(byVehicle, START, null, List.of("at-start", "middle", "at-end", "after")),
            Arguments.of(byVehicle, null, END, List.of("before", "at-start", "middle", "at-end")),
            Arguments.of(byVehicle, START, END, List.of("at-start", "middle", "at-end")),
            Arguments.of(byVehicle, START, START, List.of("at-start"))));
    }

    @ParameterizedTest
    @MethodSource("filters")
    void exportTrips_optionalFilters_applyInclusiveStartTimeBoundsAndTenantScope(
            boolean byVehicle, Instant start, Instant end, List<String> ownedIds) throws Exception {
        // given
        var request = get("/api/v1/trips/export");
        if (byVehicle) request.param("vehicleId", "owned");
        if (start != null) request.param("start", start.toString());
        if (end != null) request.param("end", end.toString());
        var expected = new ArrayList<>(ownedIds);
        if (!byVehicle) expected.add("other-owned");
        // when
        String csv = mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        // then
        assertThat(csv.lines().skip(1).map(line -> line.split(",")[0]).toList())
            .containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test void exportTrips_reversedRange_returnsBadRequest() throws Exception {
        mvc.perform(get("/api/v1/trips/export").param("start", END.toString()).param("end", START.toString()))
            .andExpect(status().isBadRequest());
    }

    @Test void exportTrips_missingTenant_returnsForbidden() throws Exception {
        TenantContext.clear();
        mvc.perform(get("/api/v1/trips/export")).andExpect(status().isForbidden());
    }

    @Test void exportTrips_foreignVehicle_returnsHeaderOnly() throws Exception {
        String csv = mvc.perform(get("/api/v1/trips/export").param("vehicleId", "foreign-vehicle"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(csv.lines()).hasSize(1);
    }

    @Test void exportTrips_matchingRecordsAcrossPages_exportsEveryMatchOnce() throws Exception {
        // given: matching timestamps exercise deterministic secondary ordering across the 500-row boundary
        Vehicle owned = em.find(Vehicle.class, "owned");
        for (int i = 0; i < 501; i++) trip("page-" + i, owned, START);
        em.flush();
        em.clear();
        // when
        String csv = mvc.perform(get("/api/v1/trips/export").param("vehicleId", "owned")
                .param("start", START.toString()).param("end", START.toString()))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        // then
        List<String> ids = csv.lines().skip(1).map(line -> line.split(",")[0]).toList();
        assertThat(ids).hasSize(502).doesNotHaveDuplicates();
        assertThat(ids).contains("at-start", "page-0", "page-500").doesNotContain("before", "after", "foreign");
    }

    private Vehicle vehicle(String id, Long organization) {
        Vehicle vehicle = new Vehicle();
        vehicle.setVehicleId(id);
        vehicle.setOrganizationId(organization);
        em.persist(vehicle);
        return vehicle;
    }

    private void trip(String id, Vehicle vehicle, Instant start) {
        Trip trip = new Trip();
        trip.setTripId(id);
        trip.setVehicle(vehicle);
        trip.setStartTime(start);
        trip.setEndTime(start.plusSeconds(60));
        trip.setIsActive(false);
        em.persist(trip);
    }
}
