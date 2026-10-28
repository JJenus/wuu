package com.surf.tracker.core.domain.entity;

import com.surf.tracker.shared.util.TimeProvider;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "trip_points")
public class TripPoint {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "trip_point_id")
    private Long tripPointId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;
    
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id", nullable = false)
    private TrackerLocation location;
    
    @Column(name = "point_order", nullable = false)
    private Integer pointOrder;
    
    @Column(name = "segment_distance_km")
    private Float segmentDistanceKm;
    
    @Column(name = "segment_duration_minutes")
    private Float segmentDurationMinutes;
    
    @Column(name = "created_at")
    private Instant createdAt = Instant.EPOCH;
    
    // Getters and Setters
    public Long getTripPointId() { return tripPointId; }
    public void setTripPointId(Long tripPointId) { this.tripPointId = tripPointId; }
    
    public Trip getTrip() { return trip; }
    public void setTrip(Trip trip) { this.trip = trip; }
    
    public TrackerLocation getLocation() { return location; }
    public void setLocation(TrackerLocation location) { this.location = location; }
    
    public Integer getPointOrder() { return pointOrder; }
    public void setPointOrder(Integer pointOrder) { this.pointOrder = pointOrder; }
    
    public Float getSegmentDistanceKm() { return segmentDistanceKm; }
    public void setSegmentDistanceKm(Float segmentDistanceKm) { this.segmentDistanceKm = segmentDistanceKm; }
    
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    
    public Float getSegmentDurationMinutes() { return segmentDurationMinutes; }
    public void setSegmentDurationMinutes(Float segmentDurationMinutes) { 
        this.segmentDurationMinutes = segmentDurationMinutes; 
    }
}