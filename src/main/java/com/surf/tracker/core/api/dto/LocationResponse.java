package com.surf.tracker.core.api.dto;

import java.time.Instant;

public class LocationResponse {
    private Double latitude;
    private Double longitude;
    private Float speedKmh;
    private Instant recordedAt;

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }

    public Float getSpeedKmh() { return speedKmh; }
    public void setSpeedKmh(Float speedKmh) { this.speedKmh = speedKmh; }

    public Instant getRecordedAt() { return recordedAt; }
    public void setRecordedAt(Instant recordedAt) { this.recordedAt = recordedAt; }
}
