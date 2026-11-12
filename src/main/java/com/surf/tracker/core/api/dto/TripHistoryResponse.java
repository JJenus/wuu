package com.surf.tracker.core.api.dto;

import java.time.Instant;
import java.util.List;

/** Recorded vehicle fixes in a trip's time window, not inferred trip membership. */
@io.swagger.v3.oas.annotations.media.Schema(description = "Recorded fixes in an authorized vehicle/time window; not interpolated route geometry or inferred trip membership. truncated=true means the requested limit omitted later results.")
public record TripHistoryResponse(String tripId, String vehicleId, Source source,
                                  Instant windowStart, Instant windowEnd,
                                  boolean truncated, List<Point> points) {
    public enum Source { VEHICLE_TIME_WINDOW }
    public record Point(Long locationId, Instant recordedAt, Double latitude,
                        Double longitude, Float speedKmh) {}
}
