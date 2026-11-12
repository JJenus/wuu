package com.surf.tracker.core.api;

import com.surf.tracker.main.api.ApiV1;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.tags.Tag;

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
@ApiV1
@RequestMapping("/trips")
@Tag(name = "Trips")
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
    @Operation(operationId = "tripsExport", summary = "Download trip summaries as CSV", description = "Requires trips.export (separately from trips.read). Optional vehicleId, start and end filter the tenant-authorized export. Dates are ISO-8601 instants. Returns text/csv with an attachment filename trips-yyyyMMdd-HHmmss.csv in UTC; no JSON pagination envelope. The controller buffers the export before returning it.")
    @ApiResponse(responseCode = "200", description = "CSV attachment", headers = @Header(name = "Content-Disposition", description = "attachment; filename=\"trips-yyyyMMdd-HHmmss.csv\"", schema = @Schema(type = "string")), content = @Content(mediaType = "text/csv", schema = @Schema(type = "string", format = "binary")))
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
