package com.surf.tracker.core.domain.entity;

import com.surf.tracker.shared.util.TimeProvider;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "tracker_location_data")
public class TrackerLocation {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "location_id")
    private Long locationId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tracker_id", nullable = false)
    private Tracker tracker;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id")
    private Trip trip;
    
    @Column(name = "latitude")
    private Double latitude;
    
    @Column(name = "longitude")
    private Double longitude;
    
    @Column(name = "speed_kmh")
    private Float speedKmh;
    
    @Column(name = "heading")
    private Float heading;
    
    @Column(name = "altitude")
    private Float altitude;
    
    @Column(name = "validity", length = 1)
    private String validity = "A";
    
    @Column(name = "odometer_km")
    private Float odometerKm;
    
    @Column(name = "battery_voltage")
    private Float batteryVoltage;
    
    @Column(name = "signal_strength")
    private Integer signalStrength;
    
    @Column(name = "acc_status")
    private Boolean accStatus;
    
    @Column(name = "engine_status", length = 20)
    private String engineStatus;
    
    @Column(name = "device_status", columnDefinition = "JSONB")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    private String deviceStatus;
    
    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;
    
    @Column(name = "created_at")
    private Instant createdAt = Instant.EPOCH;
    
    // Constructor
    public TrackerLocation() {}
    
    public TrackerLocation(Double latitude, Double longitude, Float speedKmh, Instant recordedAt) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.speedKmh = speedKmh;
        this.recordedAt = recordedAt;
    }
    
    // Business methods
    public boolean isValid() {
        return latitude != null && longitude != null && 
               latitude >= -90 && latitude <= 90 &&
               longitude >= -180 && longitude <= 180 &&
               recordedAt != null;
    }
    
    // Getters and Setters
    public Long getLocationId() { return locationId; }
    public void setLocationId(Long locationId) { this.locationId = locationId; }
    
    public Tracker getTracker() { return tracker; }
    public void setTracker(Tracker tracker) { this.tracker = tracker; }
    
    public Trip getTrip() { return trip; }
    public void setTrip(Trip trip) { this.trip = trip; }
    
    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }
    
    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }
    
    public Float getSpeedKmh() { return speedKmh; }
    public void setSpeedKmh(Float speedKmh) { this.speedKmh = speedKmh; }
    
    public Instant getRecordedAt() { return recordedAt; }
    public void setRecordedAt(Instant recordedAt) { this.recordedAt = recordedAt; }
    
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    
    // Additional getters/setters
    public Float getHeading() { return heading; }
    public void setHeading(Float heading) { this.heading = heading; }
    
    public Float getAltitude() { return altitude; }
    public void setAltitude(Float altitude) { this.altitude = altitude; }
    
    public String getValidity() { return validity; }
    public void setValidity(String validity) { this.validity = validity; }
    
    public Float getOdometerKm() { return odometerKm; }
    public void setOdometerKm(Float odometerKm) { this.odometerKm = odometerKm; }
    
    public Float getBatteryVoltage() { return batteryVoltage; }
    public void setBatteryVoltage(Float batteryVoltage) { this.batteryVoltage = batteryVoltage; }
    
    public Integer getSignalStrength() { return signalStrength; }
    public void setSignalStrength(Integer signalStrength) { this.signalStrength = signalStrength; }
    
    public Boolean getAccStatus() { return accStatus; }
    public void setAccStatus(Boolean accStatus) { this.accStatus = accStatus; }
    
    public String getEngineStatus() { return engineStatus; }
    public void setEngineStatus(String engineStatus) { this.engineStatus = engineStatus; }
    
    public String getDeviceStatus() { return deviceStatus; }
    public void setDeviceStatus(String deviceStatus) { this.deviceStatus = deviceStatus; }
}
