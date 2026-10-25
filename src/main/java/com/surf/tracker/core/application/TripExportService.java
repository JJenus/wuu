package com.surf.tracker.core.application;

import com.surf.tracker.core.domain.entity.Trip;
import com.surf.tracker.core.infrastructure.repository.TripRepository;
import com.surf.tracker.shared.security.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Service
@Transactional(readOnly = true)
public class TripExportService {

    private static final String CSV_HEADER =
            "trip_id,vehicle_id,start_time,end_time,duration_minutes," +
            "start_reason,end_reason,start_lat,start_lon,end_lat,end_lon," +
            "total_distance_km,avg_speed_kmh,max_speed_kmh,idle_minutes," +
            "fuel_liters,is_active";

    private static final DateTimeFormatter DF = DateTimeFormatter
            .ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
            .withZone(ZoneId.of("UTC"));

    private final TripRepository tripRepository;
    private final Clock clock;

    public TripExportService(TripRepository tripRepository, Clock clock) {
        this.tripRepository = tripRepository;
        this.clock = clock;
    }

    public void exportToCsv(
            String vehicleId,
            Instant startDate,
            Instant endDate,
            OutputStream out
    ) {
        Long orgId = TenantContext.getCurrentOrgId();
        try (PrintWriter w = new PrintWriter(out)) {
            w.println(CSV_HEADER);

            Consumer<Trip> rowWriter = trip -> {
                String[] row = toRow(trip);
                w.println(String.join(",", row));
            };

            if (vehicleId != null && startDate != null && endDate != null) {
                findTripsByVehicleAndRange(vehicleId, startDate, endDate, orgId, rowWriter);
            } else if (vehicleId != null && startDate != null) {
                tripsByVehicleSince(vehicleId, startDate, orgId, rowWriter);
            } else if (vehicleId != null) {
                tripsByVehicle(vehicleId, orgId, rowWriter);
            } else {
                Pageable pageable = PageRequest.of(0, 500);
                Page<Trip> page;
                do {
                    page = tripRepository.findByOrganizationId(orgId, pageable);
                    page.forEach(rowWriter);
                    pageable = pageable.next();
                } while (page.hasNext());
            }
        }
    }

    private void tripsByVehicle(String vehicleId, Long orgId, Consumer<Trip> rowWriter) {
        Pageable pageable = PageRequest.of(0, 500);
        Page<Trip> page;
        do {
            page = tripRepository.findByVehicleVehicleIdAndOrganizationId(vehicleId, orgId, pageable);
            page.forEach(rowWriter);
            pageable = pageable.next();
        } while (page.hasNext());
    }

    private void tripsByVehicleSince(String vehicleId, Instant startDate, Long orgId, Consumer<Trip> rowWriter) {
        Pageable pageable = PageRequest.of(0, 500);
        Page<Trip> page;
        do {
            page = tripRepository.findByVehicleVehicleIdAndOrganizationId(vehicleId, orgId, pageable);
            page.getContent().stream()
                    .filter(t -> t.getStartTime().compareTo(startDate) >= 0)
                    .forEach(rowWriter);
            pageable = pageable.next();
        } while (page.hasNext());
    }

    private void findTripsByVehicleAndRange(String vehicleId, Instant startDate, Instant endDate, Long orgId, Consumer<Trip> rowWriter) {
        Pageable pageable = PageRequest.of(0, 500);
        Page<Trip> page;
        do {
            page = tripRepository.findByVehicleVehicleIdAndStartTimeBetweenAndOrganizationId(
                    vehicleId, startDate, endDate, orgId, pageable);
            page.forEach(rowWriter);
            pageable = pageable.next();
        } while (page.hasNext());
    }

    private String[] toRow(Trip trip) {
        return new String[] {
                escape(trip.getTripId()),
                escape(trip.getVehicle() != null ? trip.getVehicle().getVehicleId() : ""),
                trip.getStartTime() != null ? DF.format(trip.getStartTime()) : "",
                trip.getEndTime() != null ? DF.format(trip.getEndTime()) : "",
                String.valueOf(durationMinutes(trip)),
                escape(trip.getStartReason() != null ? trip.getStartReason().name() : ""),
                escape(trip.getEndReason() != null ? trip.getEndReason().name() : ""),
                trip.getStartLocation() != null ? String.valueOf(trip.getStartLocation().getLatitude()) : "",
                trip.getStartLocation() != null ? String.valueOf(trip.getStartLocation().getLongitude()) : "",
                trip.getEndLocation() != null ? String.valueOf(trip.getEndLocation().getLatitude()) : "",
                trip.getEndLocation() != null ? String.valueOf(trip.getEndLocation().getLongitude()) : "",
                trip.getTotalDistanceKm() != null ? String.valueOf(trip.getTotalDistanceKm()) : "",
                trip.getAverageSpeedKmh() != null ? String.valueOf(trip.getAverageSpeedKmh()) : "",
                trip.getMaxSpeedKmh() != null ? String.valueOf(trip.getMaxSpeedKmh()) : "",
                trip.getIdleTimeMinutes() != null ? String.valueOf(trip.getIdleTimeMinutes()) : "",
                trip.getFuelConsumedLiters() != null ? String.valueOf(trip.getFuelConsumedLiters()) : "",
                String.valueOf(Boolean.TRUE.equals(trip.getIsActive()))
        };
    }

    private long durationMinutes(Trip trip) {
        if (trip.getStartTime() == null) return 0;
        Instant end = trip.getEndTime() != null ? trip.getEndTime() : clock.instant();
        return java.time.Duration.between(trip.getStartTime(), end).toMinutes();
    }

    private String escape(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
