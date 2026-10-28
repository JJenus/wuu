package com.surf.tracker.core.api;

import com.surf.tracker.core.application.TripExportService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/api/v1/trips")
public class TripExportController {

    private static final DateTimeFormatter FILENAME_TS =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    private final TripExportService tripExportService;
    private final Clock clock;

    public TripExportController(TripExportService tripExportService, Clock clock) {
        this.tripExportService = tripExportService;
        this.clock = clock;
    }

    @GetMapping("/export")
    public ResponseEntity<InputStreamResource> exportTrips(
            @RequestParam(value = "vehicleId", required = false) String vehicleId,
            @RequestParam(value = "start", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @RequestParam(value = "end", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate) {

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        tripExportService.exportToCsv(vehicleId, startDate, endDate, buffer);

        String filename = String.format("trips-%s.csv",
                FILENAME_TS.format(clock.instant()));

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(new InputStreamResource(new ByteArrayInputStream(buffer.toByteArray())));
    }
}
