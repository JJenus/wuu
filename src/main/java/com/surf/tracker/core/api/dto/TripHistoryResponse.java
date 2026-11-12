package com.surf.tracker.core.api.dto;

import java.time.Instant;
import java.util.List;

/** Recorded vehicle fixes in a trip's time window, not inferred trip membership. */
public record TripHistoryResponse(String tripId, String vehicleId, Source source,
                                  Instant windowStart, Instant windowEnd,
                                  boolean truncated, List<Point> points) {
    public enum Source { VEHICLE_TIME_WINDOW }
    public record Point(Long locationId, Instant recordedAt, Double latitude,
                        Double longitude, Float speedKmh) {}
}
