package com.surf.tracker.core.api.dto;

import java.time.Instant;

public class TripResponse {
    private String tripId;
    private String vehicleId;
    private Instant startTime;
    private Instant endTime;
    private Boolean isActive;
    private Float totalDistanceKm;
    private Float averageSpeedKmh;
    private Float maxSpeedKmh;
    private Float fuelConsumedLiters;
    private String startReason;
    private String endReason;

    public String getTripId() { return tripId; }
    public void setTripId(String tripId) { this.tripId = tripId; }

    public String getVehicleId() { return vehicleId; }
    public void setVehicleId(String vehicleId) { this.vehicleId = vehicleId; }

    public Instant getStartTime() { return startTime; }
    public void setStartTime(Instant startTime) { this.startTime = startTime; }

    public Instant getEndTime() { return endTime; }
    public void setEndTime(Instant endTime) { this.endTime = endTime; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public Float getTotalDistanceKm() { return totalDistanceKm; }
    public void setTotalDistanceKm(Float totalDistanceKm) { this.totalDistanceKm = totalDistanceKm; }

    public Float getAverageSpeedKmh() { return averageSpeedKmh; }
    public void setAverageSpeedKmh(Float averageSpeedKmh) { this.averageSpeedKmh = averageSpeedKmh; }

    public Float getMaxSpeedKmh() { return maxSpeedKmh; }
    public void setMaxSpeedKmh(Float maxSpeedKmh) { this.maxSpeedKmh = maxSpeedKmh; }

    public Float getFuelConsumedLiters() { return fuelConsumedLiters; }
    public void setFuelConsumedLiters(Float fuelConsumedLiters) { this.fuelConsumedLiters = fuelConsumedLiters; }

    public String getStartReason() { return startReason; }
    public void setStartReason(String startReason) { this.startReason = startReason; }

    public String getEndReason() { return endReason; }
    public void setEndReason(String endReason) { this.endReason = endReason; }
}
