package com.surf.tracker.shared.domain;

import java.time.Instant;

public record LocationPoint(
    double latitude,
    double longitude,
    float speedKmh,
    Instant timestamp
) {
    public boolean isValid() {
        return latitude >= -90 && latitude <= 90 &&
               longitude >= -180 && longitude <= 180 &&
               speedKmh >= 0 &&
               timestamp != null;
    }

    public double distanceTo(LocationPoint other) {
        final int R = 6371;

        double latDistance = Math.toRadians(other.latitude() - this.latitude());
        double lonDistance = Math.toRadians(other.longitude() - this.longitude());

        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                 + Math.cos(Math.toRadians(this.latitude()))
                 * Math.cos(Math.toRadians(other.latitude()))
                 * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return R * c;
    }
}
