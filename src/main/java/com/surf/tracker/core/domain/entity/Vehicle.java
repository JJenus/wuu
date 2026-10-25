package com.surf.tracker.core.domain.entity;

import com.surf.tracker.core.domain.enums.EngineState;
import com.surf.tracker.core.domain.enums.TripEndReason;
import com.surf.tracker.core.domain.enums.TripStartReason;
import com.surf.tracker.core.exception.TripException;
import com.surf.tracker.core.exception.VehicleException;
import com.surf.tracker.shared.domain.LocationPoint;
import com.surf.tracker.shared.util.TimeProvider;
import jakarta.persistence.*;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "vehicles")
public class Vehicle {

    @Id
    @Column(name = "vehicle_id", length = 50)
    private String vehicleId;

    @Column(name = "device_id", length = 50, unique = true)
    private String deviceId;

    @Column(name = "model", length = 100)
    private String model;

    @Column(name = "license_plate", length = 20)
    private String licensePlate;

    @Column(name = "vin", length = 17)
    private String vin;

    @Enumerated(EnumType.STRING)
    @Column(name = "engine_state", length = 20)
    private EngineState engineState = EngineState.OFF;

    @Column(name = "acc_status")
    private Boolean accStatus = false;

    @Column(name = "last_acc_on_time")
    private Instant lastAccOnTime;

    @Column(name = "last_acc_off_time")
    private Instant lastAccOffTime;

    @Column(name = "last_telemetry_time")
    private Instant lastTelemetryTime;

    @Column(name = "fuel_level")
    private Float fuelLevel;

    @Column(name = "odometer_km")
    private Float odometerKm;

    @Column(name = "is_fuel_cut_active")
    private Boolean fuelCutActive = false;

    @Column(name = "last_movement_time")
    private Instant lastMovementTime;

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "current_location_id")
    private TrackerLocation currentLocation;

    @OneToMany(mappedBy = "vehicle", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Tracker> trackers = new ArrayList<>();

    @Column(name = "organization_id")
    private Long organizationId;

    @OneToMany(mappedBy = "vehicle", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Trip> trips = new ArrayList<>();

    @Column(name = "created_at")
    private Instant createdAt = Instant.EPOCH;

    @Column(name = "updated_at")
    private Instant updatedAt = Instant.EPOCH;

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = TimeProvider.now();
    }

    public void processNewTelemetry(LocationPoint newLocation, Instant now) {
        if (!newLocation.isValid()) {
            throw VehicleException.invalidLocationData();
        }

        if (newLocation.speedKmh() > 0) {
            this.engineState = EngineState.MOVING;
            this.lastMovementTime = now;

            if (getActiveTrip() == null) {
                startTrip(newLocation, now);
            }
        } else if (this.engineState == EngineState.MOVING) {
            this.engineState = EngineState.IDLE;
            this.lastMovementTime = now;
        }
    }

    private void startTrip(LocationPoint startLocation, Instant now) {
        if (getActiveTrip() != null) {
            throw TripException.alreadyActive(vehicleId);
        }
        Trip trip = new Trip();
        trip.setTripId("TRIP_" + vehicleId + "_" + TimeProvider.newId().substring(0, 8));
        trip.setVehicle(this);
        trip.setStartLocation(null);
        trip.setStartTime(now);
            trip.setStartReason(TripStartReason.AUTO_DETECTED);
        trip.setIsActive(true);
        trip.setTotalDistanceKm(0.0f);
        addTrip(trip);
    }

    public void issueFuelCutOffCommand(Instant now) {
        float speed = currentLocation != null ? currentLocation.getSpeedKmh() : 0.0f;
        if (speed > 10) {
            throw VehicleException.fuelCutNotAllowedWhileMoving(speed);
        }
        if (Boolean.TRUE.equals(this.fuelCutActive)) {
            throw VehicleException.fuelCutAlreadyActive();
        }
        this.fuelCutActive = true;
        this.engineState = EngineState.FUEL_CUT_ACTIVE;
    }

    public void issueFuelRestoreCommand(Instant now) {
        this.fuelCutActive = false;
        float speed = currentLocation != null ? currentLocation.getSpeedKmh() : 0.0f;
        if (speed > 0) {
            this.engineState = EngineState.MOVING;
        } else {
            this.engineState = EngineState.IDLE;
        }
    }

    public Duration getIdleDuration(Instant now) {
        if (this.lastMovementTime == null || this.engineState != EngineState.IDLE) {
            return Duration.ZERO;
        }
        return Duration.between(this.lastMovementTime, now);
    }

    public LocationPoint getCurrentLocationPoint() {
        if (currentLocation == null) {
            return null;
        }
        return new LocationPoint(
            currentLocation.getLatitude(),
            currentLocation.getLongitude(),
            currentLocation.getSpeedKmh() != null ? currentLocation.getSpeedKmh() : 0.0f,
            currentLocation.getRecordedAt()
        );
    }
    
    // Business methods
    public void updateAccStatus(boolean accOn, Instant timestamp) {
        this.accStatus = accOn;
        
        if (accOn) {
            this.lastAccOnTime = timestamp;
        } else {
            this.lastAccOffTime = timestamp;
        }
        
        this.lastTelemetryTime = timestamp;
    }
    
    public void updateLocation(TrackerLocation location) {
        this.currentLocation = location;
        this.lastTelemetryTime = location.getRecordedAt();
    }
    
    public Trip getActiveTrip() {
        return trips.stream()
            .filter(Trip::getIsActive)
            .findFirst()
            .orElse(null);
    }
    
    public void addTrip(Trip trip) {
        trips.add(trip);
        trip.setVehicle(this);
    }
    
    public void endActiveTrip(TripEndReason reason, TrackerLocation endLocation) {
        Trip activeTrip = getActiveTrip();
        if (activeTrip != null) {
            activeTrip.endTrip(reason, endLocation);
        }
    }
    
    // Getters and Setters
    public String getVehicleId() { return vehicleId; }
    public void setVehicleId(String vehicleId) { this.vehicleId = vehicleId; }
    
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    
    public EngineState getEngineState() { return engineState; }
    public void setEngineState(EngineState engineState) { this.engineState = engineState; }
    
    public Boolean getAccStatus() { return accStatus; }
    public void setAccStatus(Boolean accStatus) { this.accStatus = accStatus; }
    
    public Instant getLastAccOnTime() { return lastAccOnTime; }
    public void setLastAccOnTime(Instant lastAccOnTime) { this.lastAccOnTime = lastAccOnTime; }
    
    public Instant getLastAccOffTime() { return lastAccOffTime; }
    public void setLastAccOffTime(Instant lastAccOffTime) { this.lastAccOffTime = lastAccOffTime; }
    
    public Instant getLastTelemetryTime() { return lastTelemetryTime; }
    public void setLastTelemetryTime(Instant lastTelemetryTime) { this.lastTelemetryTime = lastTelemetryTime; }
    
    public TrackerLocation getCurrentLocation() { return currentLocation; }
    public void setCurrentLocation(TrackerLocation currentLocation) { this.currentLocation = currentLocation; }
    
    public List<Trip> getTrips() { return trips; }
    public void setTrips(List<Trip> trips) { this.trips = trips; }
    
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    
    public Long getOrganizationId() { return organizationId; }
    public void setOrganizationId(Long organizationId) { this.organizationId = organizationId; }
    
    // Additional getters/setters
    public String getLicensePlate() { return licensePlate; }
    public void setLicensePlate(String licensePlate) { this.licensePlate = licensePlate; }
    
    public String getVin() { return vin; }
    public void setVin(String vin) { this.vin = vin; }
    
    public Float getFuelLevel() { return fuelLevel; }
    public void setFuelLevel(Float fuelLevel) { this.fuelLevel = fuelLevel; }
    
    public Float getOdometerKm() { return odometerKm; }
    public void setOdometerKm(Float odometerKm) { this.odometerKm = odometerKm; }
    
    public Boolean getFuelCutActive() { return fuelCutActive; }
    public void setFuelCutActive(Boolean fuelCutActive) { this.fuelCutActive = fuelCutActive; }

    public void addTracker(Tracker tracker) {
        trackers.add(tracker);
        tracker.setVehicle(this);
    }

    public void removeTracker(Tracker tracker) {
        trackers.remove(tracker);
        tracker.setVehicle(null);
    }

    public Tracker getActiveTracker() {
        return trackers.stream()
                .filter(t -> Boolean.TRUE.equals(t.getIsOnline()))
                .findFirst()
                .orElse(null);
    }

    // Getters and setters
    public List<Tracker> getTrackers() { return trackers; }
    public void setTrackers(List<Tracker> trackers) { this.trackers = trackers; }
}