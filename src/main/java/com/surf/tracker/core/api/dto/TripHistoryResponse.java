package com.surf.tracker.core.api.dto;

import java.time.Instant;
import java.util.List;

/** Explicit trip fixes for new trips; labelled time-window records for legacy trips. */
@io.swagger.v3.oas.annotations.media.Schema(description = "TRIP_ASSOCIATED contains explicit saved trip membership; VEHICLE_TIME_WINDOW is the legacy vehicle/time-window view. Neither interpolates route geometry. truncated=true means the requested limit omitted later results.")
public record TripHistoryResponse(String tripId, String vehicleId, Source source,
                                  Instant windowStart, Instant windowEnd,
                                  boolean truncated, List<Point> points) {
    public enum Source { VEHICLE_TIME_WINDOW, TRIP_ASSOCIATED }
    public record Point(Long locationId, Instant recordedAt, Double latitude,
                        Double longitude, Float speedKmh) {}
}
