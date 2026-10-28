package com.surf.tracker.alerting.domain;

import com.surf.tracker.shared.domain.LocationPoint;
import java.time.Instant;

public class LocationPointTestBuilder {
    private Double latitude = 40.7128;
    private Double longitude = -74.0060;
    private Float speedKmh = 60.0f;
    private Instant timestamp = Instant.now();

    private LocationPointTestBuilder() {}

    public static LocationPointTestBuilder defaultLocation() {
        return new LocationPointTestBuilder();
    }

    public static LocationPointTestBuilder overspeedLocation() {
        return new LocationPointTestBuilder().speedKmh(100.0f);
    }

    public static LocationPointTestBuilder stationaryLocation() {
        return new LocationPointTestBuilder().speedKmh(0.0f);
    }

    public static LocationPointTestBuilder insideGeofence() {
        return new LocationPointTestBuilder()
            .latitude(40.7128)
            .longitude(-74.0060);
    }

    public static LocationPointTestBuilder outsideGeofence() {
        return new LocationPointTestBuilder()
            .latitude(41.7128)
            .longitude(-75.0060);
    }

    public LocationPointTestBuilder latitude(Double latitude) {
        this.latitude = latitude;
        return this;
    }

    public LocationPointTestBuilder longitude(Double longitude) {
        this.longitude = longitude;
        return this;
    }

    public LocationPointTestBuilder speedKmh(Float speedKmh) {
        this.speedKmh = speedKmh;
        return this;
    }

    public LocationPointTestBuilder timestamp(Instant timestamp) {
        this.timestamp = timestamp;
        return this;
    }

    public LocationPoint build() {
        return new LocationPoint(latitude, longitude, speedKmh, timestamp);
    }
}
