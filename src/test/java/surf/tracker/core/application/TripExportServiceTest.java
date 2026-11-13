package com.surf.tracker.core.application;

import com.surf.tracker.core.domain.entity.Trip;
import com.surf.tracker.core.domain.entity.Vehicle;
import com.surf.tracker.core.domain.enums.TripEndReason;
import com.surf.tracker.core.domain.enums.TripStartReason;
import com.surf.tracker.core.infrastructure.repository.TripRepository;
import com.surf.tracker.shared.security.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TripExportServiceTest {

    private static final Long ORG_ID = 1L;

    @Mock
    private TripRepository tripRepository;

    private TripExportService service;

    private Trip trip;
    private Vehicle vehicle;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentOrgId(ORG_ID);
        service = new TripExportService(tripRepository,
            Clock.fixed(Instant.parse("2026-08-30T11:00:00Z"), ZoneOffset.UTC));

        vehicle = new Vehicle();
        vehicle.setVehicleId("vehicle-001");

        trip = new Trip();
        trip.setTripId("trip-1");
        trip.setVehicle(vehicle);
        trip.setStartTime(Instant.parse("2026-08-30T10:00:00Z"));
        trip.setEndTime(Instant.parse("2026-08-30T11:00:00Z"));
        trip.setStartReason(TripStartReason.ACC_ON);
        trip.setEndReason(TripEndReason.ACC_OFF);
        trip.setTotalDistanceKm(45.5f);
        trip.setAverageSpeedKmh(45.5f);
        trip.setMaxSpeedKmh(80.0f);
        trip.setIdleTimeMinutes(2);
        trip.setFuelConsumedLiters(3.5f);
        trip.setIsActive(false);
    }

    @AfterEach
    void clearTenant() { TenantContext.clear(); }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void exportToCsv_nonPositiveTenant_returnsForbidden(long organization) {
        // given
        TenantContext.setCurrentOrgId(organization);
        // when / then
        assertThatThrownBy(() -> service.exportToCsv(null, null, null, new ByteArrayOutputStream()))
            .isInstanceOfSatisfying(org.springframework.web.server.ResponseStatusException.class,
                error -> assertThat(error.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.FORBIDDEN));
    }

    @Test
    void exportToCsv_activeTrip_reportsElapsedDurationWithoutInventingEndTime() {
        // given
        trip.setEndTime(null);
        trip.setIsActive(true);
        when(tripRepository.findAll(org.mockito.ArgumentMatchers.<Specification<Trip>>any(), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(trip)));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        // when
        service.exportToCsv("vehicle-001", null, null, out);
        // then
        String row = out.toString(StandardCharsets.UTF_8).lines().skip(1).findFirst().orElseThrow();
        assertThat(row).contains("2026-08-30T10:00:00Z,,60,").endsWith(",true");
    }

    @Test
    void exportToCsv_writesHeaderAndRow() {
        when(tripRepository.findAll(org.mockito.ArgumentMatchers.<Specification<Trip>>any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(trip)));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        service.exportToCsv("vehicle-001", null, null, out);

        String csv = out.toString(StandardCharsets.UTF_8);
        assertThat(csv).contains("trip_id,vehicle_id,start_time,end_time");
        assertThat(csv).contains("trip-1,vehicle-001");
        assertThat(csv).contains("2026-08-30T10:00:00Z");
        assertThat(csv).contains("ACC_ON,ACC_OFF");
        assertThat(csv).contains("45.5");
    }

    @Test
    void exportToCsv_allVehicles_noFilter() {
        when(tripRepository.findAll(org.mockito.ArgumentMatchers.<Specification<Trip>>any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(trip)));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        service.exportToCsv(null, null, null, out);

        String csv = out.toString(StandardCharsets.UTF_8);
        assertThat(csv).contains("trip-1");
    }

    @Test
    void exportToCsv_withDateRange_writesMatchingRows() {
        Instant start = Instant.parse("2026-08-30T00:00:00Z");
        Instant end = Instant.parse("2026-08-30T23:59:59Z");
        when(tripRepository.findAll(org.mockito.ArgumentMatchers.<Specification<Trip>>any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(trip)));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        service.exportToCsv("vehicle-001", start, end, out);

        String csv = out.toString(StandardCharsets.UTF_8);
        assertThat(csv).contains("trip-1");
    }

    @Test
    void exportToCsv_emptyResult_writesHeaderOnly() {
        when(tripRepository.findAll(org.mockito.ArgumentMatchers.<Specification<Trip>>any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        service.exportToCsv("vehicle-001", null, null, out);

        String csv = out.toString(StandardCharsets.UTF_8);
        String[] lines = csv.split("\n");
        assertThat(lines).hasSize(1);
        assertThat(lines[0]).contains("trip_id");
    }

    @Test
    void exportToCsv_escapesCommasInValues() {
        Vehicle v = new Vehicle();
        v.setVehicleId("v,1,with,commas");
        trip.setVehicle(v);
        when(tripRepository.findAll(org.mockito.ArgumentMatchers.<Specification<Trip>>any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(trip)));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        service.exportToCsv("v,1,with,commas", null, null, out);

        String csv = out.toString(StandardCharsets.UTF_8);
        assertThat(csv).contains("\"v,1,with,commas\"");
    }
}
