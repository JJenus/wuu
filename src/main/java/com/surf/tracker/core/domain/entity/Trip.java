package com.surf.tracker.core.domain.entity;

import com.surf.tracker.core.domain.enums.TripEndReason;
import com.surf.tracker.core.domain.enums.TripStartReason;
import com.surf.tracker.shared.util.TimeProvider;
import jakarta.persistence.*;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "trips")
public class Trip {
    
    @Id
    @Column(name = "trip_id", length = 50)
    private String tripId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;
    
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "start_location_id")
    private TrackerLocation startLocation;
    
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "end_location_id")
    private TrackerLocation endLocation;
    
    @Column(name = "start_time", nullable = false)
    private Instant startTime;
    
    @Column(name = "end_time")
    private Instant endTime;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "start_reason", length = 50)
    private TripStartReason startReason;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "end_reason", length = 50)
    private TripEndReason endReason;
    
    @Column(name = "acc_off_duration_seconds")
    private Integer accOffDurationSeconds;
    
    @Column(name = "total_distance_km")
    private Float totalDistanceKm = 0.0f;
    
    @Column(name = "average_speed_kmh")
    private Float averageSpeedKmh;
    
    @Column(name = "max_speed_kmh")
    private Float maxSpeedKmh;
    
    @Column(name = "idle_time_minutes")
    private Integer idleTimeMinutes;
    
    @Column(name = "fuel_consumed_liters")
    private Float fuelConsumedLiters;
    
    @Column(name = "is_active")
    private Boolean isActive = true;
    
    @OneToMany(mappedBy = "trip", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("pointOrder ASC")
    private List<TripPoint> tripPoints = new ArrayList<>();
    
    @Column(name = "created_at")
    private Instant createdAt = Instant.EPOCH;

    @Column(name = "updated_at")
    private Instant updatedAt = Instant.EPOCH;

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = TimeProvider.now();
    }
    
    // Business methods
    public void addLocationPoint(TrackerLocation location, Float segmentDistance) {
        TripPoint tripPoint = new TripPoint();
        tripPoint.setTrip(this);
        tripPoint.setLocation(location);
        tripPoint.setPointOrder(tripPoints.size() + 1);
        tripPoint.setSegmentDistanceKm(segmentDistance);
        
        tripPoints.add(tripPoint);
        
        // Update total distance
        this.totalDistanceKm += segmentDistance;
        
        // Update max speed
        if (location.getSpeedKmh() != null && 
            (this.maxSpeedKmh == null || location.getSpeedKmh() > this.maxSpeedKmh)) {
            this.maxSpeedKmh = location.getSpeedKmh();
        }
    }
    
    public void endTrip(TripEndReason reason, TrackerLocation endLocation) {
        this.endTime = endLocation.getRecordedAt();
        this.endLocation = endLocation;
        this.endReason = reason;
        this.isActive = false;
        
        // Calculate final statistics
        calculateStatistics();
        
        // Add final location point
        if (endLocation != null) {
            TrackerLocation lastLocation = getLastLocationPoint();
            if (lastLocation != null) {
                Float segmentDistance = calculateDistance(lastLocation, endLocation);
                addLocationPoint(endLocation, segmentDistance);
            }
        }
    }
    
    public void calculateStatistics() {
        if (startTime != null && endTime != null) {
            Duration duration = Duration.between(startTime, endTime);
            long hours = duration.toHours();
            
            if (hours > 0 && totalDistanceKm != null && totalDistanceKm > 0) {
                this.averageSpeedKmh = totalDistanceKm / hours;
            }
        }
    }
    
    private TrackerLocation getLastLocationPoint() {
        return tripPoints.isEmpty() ? null : 
               tripPoints.get(tripPoints.size() - 1).getLocation();
    }
    
    public Float calculateDistance(TrackerLocation loc1, TrackerLocation loc2) {
        if (loc1 == null || loc2 == null
                || loc1.getLatitude() == null || loc1.getLongitude() == null
                || loc2.getLatitude() == null || loc2.getLongitude() == null) {
            return 0.0f;
        }
        double earthRadiusKm = 6371.0;
        double dLat = Math.toRadians(loc2.getLatitude() - loc1.getLatitude());
        double dLon = Math.toRadians(loc2.getLongitude() - loc1.getLongitude());
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(loc1.getLatitude()))
                * Math.cos(Math.toRadians(loc2.getLatitude()))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return (float) (earthRadiusKm * c);
    }
    
    public Duration getDuration() {
        if (isActive) {
            return Duration.between(startTime, Instant.now());
        } else {
            return Duration.between(startTime, endTime);
        }
    }
    
    // Getters and Setters
    public String getTripId() { return tripId; }
    public void setTripId(String tripId) { this.tripId = tripId; }
    
    public Vehicle getVehicle() { return vehicle; }
    public void setVehicle(Vehicle vehicle) { this.vehicle = vehicle; }
    
    public TrackerLocation getStartLocation() { return startLocation; }
    public void setStartLocation(TrackerLocation startLocation) { this.startLocation = startLocation; }
    
    public TrackerLocation getEndLocation() { return endLocation; }
    public void setEndLocation(TrackerLocation endLocation) { this.endLocation = endLocation; }
    
    public Instant getStartTime() { return startTime; }
    public void setStartTime(Instant startTime) { this.startTime = startTime; }
    
    public Instant getEndTime() { return endTime; }
    public void setEndTime(Instant endTime) { this.endTime = endTime; }
    
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
    
    public List<TripPoint> getTripPoints() { return tripPoints; }
    public void setTripPoints(List<TripPoint> tripPoints) { this.tripPoints = tripPoints; }
    
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    
    // Additional getters/setters
    public TripStartReason getStartReason() { return startReason; }
    public void setStartReason(TripStartReason startReason) { this.startReason = startReason; }
    
    public TripEndReason getEndReason() { return endReason; }
    public void setEndReason(TripEndReason endReason) { this.endReason = endReason; }
    
    public Integer getAccOffDurationSeconds() { return accOffDurationSeconds; }
    public void setAccOffDurationSeconds(Integer accOffDurationSeconds) { 
        this.accOffDurationSeconds = accOffDurationSeconds; 
    }
    
    public Float getTotalDistanceKm() { return totalDistanceKm; }
    public void setTotalDistanceKm(Float totalDistanceKm) { this.totalDistanceKm = totalDistanceKm; }
    
    public Float getAverageSpeedKmh() { return averageSpeedKmh; }
    public void setAverageSpeedKmh(Float averageSpeedKmh) { this.averageSpeedKmh = averageSpeedKmh; }
    
    public Float getMaxSpeedKmh() { return maxSpeedKmh; }
    public void setMaxSpeedKmh(Float maxSpeedKmh) { this.maxSpeedKmh = maxSpeedKmh; }
    
    public Integer getIdleTimeMinutes() { return idleTimeMinutes; }
    public void setIdleTimeMinutes(Integer idleTimeMinutes) { this.idleTimeMinutes = idleTimeMinutes; }
    
    public Float getFuelConsumedLiters() { return fuelConsumedLiters; }
    public void setFuelConsumedLiters(Float fuelConsumedLiters) { this.fuelConsumedLiters = fuelConsumedLiters; }
}