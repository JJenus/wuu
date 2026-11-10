package com.surf.tracker.core.domain.entity;

import com.surf.tracker.core.domain.enums.TrackerStatus;
import com.surf.tracker.shared.util.TimeProvider;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "trackers")
public class Tracker {
    
    @Id
    @Column(name = "tracker_id", length = 50)
    private String trackerId;
    
    @Column(name = "device_id", length = 50, unique = true)
    private String deviceId;

    //    device_id == imei
//    @Column(name = "imei", length = 20, unique = true)
//    private String imei;
    
    @Column(name = "model", length = 100)
    private String model;
    
    @Column(name = "protocol", length = 20)
    private String protocol;
    
    @Column(name = "firmware_version", length = 50)
    private String firmwareVersion;
    
    @Column(name = "sim_number", length = 20)
    private String simNumber;
    
    @Column(name = "battery_level")
    private Float batteryLevel;
    
    @Column(name = "signal_strength")
    private Integer signalStrength;
    
    @Column(name = "is_online")
    private Boolean isOnline = false;
    
    @Column(name = "last_seen")
    private Instant lastSeen;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private TrackerStatus status = TrackerStatus.ACTIVE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id")
    private Vehicle vehicle;
    
    @OneToMany(mappedBy = "tracker", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("recordedAt DESC")
    private List<TrackerLocation> locations = new ArrayList<>();
    
    @OneToMany(mappedBy = "tracker", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TrackerRawData> rawDataEntries = new ArrayList<>();
    
    @OneToMany(mappedBy = "tracker", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DeviceCommand> commands = new ArrayList<>();
    
    @Column(name = "created_at")
    private Instant createdAt = Instant.EPOCH;

    @Column(name = "updated_at")
    private Instant updatedAt = Instant.EPOCH;

    @PrePersist
    protected void onCreate() {
        Instant now = TimeProvider.now();
        if (this.createdAt == null || Instant.EPOCH.equals(this.createdAt)) {
            this.createdAt = now;
        }
        if (this.updatedAt == null || Instant.EPOCH.equals(this.updatedAt)) {
            this.updatedAt = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = TimeProvider.now();
    }

    // Business methods
    public void updateLastSeen() {
        this.lastSeen = TimeProvider.now();
        this.isOnline = true;
    }
    
    public void updateBatteryLevel(Float level) {
        this.batteryLevel = level;
    }
    
    public void addLocation(TrackerLocation location) {
        locations.add(location);
        location.setTracker(this);
    }
    
    public void addRawData(TrackerRawData rawData) {
        rawDataEntries.add(rawData);
        rawData.setTracker(this);
    }
    
    public TrackerLocation getLatestLocation() {
        return locations.isEmpty() ? null : locations.get(0);
    }
    
    // Getters and Setters
    public String getTrackerId() { return trackerId; }
    public void setTrackerId(String trackerId) { this.trackerId = trackerId; }
    
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

//    device_id == imei
//    public String getImei() { return imei; }
//    public void setImei(String imei) { this.imei = imei; }
    
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    
    public String getProtocol() { return protocol; }
    public void setProtocol(String protocol) { this.protocol = protocol; }
    
    public Boolean getIsOnline() { return isOnline; }
    public void setIsOnline(Boolean isOnline) { this.isOnline = isOnline; }
    
    public Instant getLastSeen() { return lastSeen; }
    public void setLastSeen(Instant lastSeen) { this.lastSeen = lastSeen; }
    
    public TrackerStatus getStatus() { return status; }
    public void setStatus(TrackerStatus status) { this.status = status; }
    
    public Vehicle getVehicle() { return vehicle; }
    public void setVehicle(Vehicle vehicle) { this.vehicle = vehicle; }
    
    public List<TrackerLocation> getLocations() { return locations; }
    public void setLocations(List<TrackerLocation> locations) { this.locations = locations; }
    
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    
    // Additional getters/setters
    public String getFirmwareVersion() { return firmwareVersion; }
    public void setFirmwareVersion(String firmwareVersion) { this.firmwareVersion = firmwareVersion; }
    
    public String getSimNumber() { return simNumber; }
    public void setSimNumber(String simNumber) { this.simNumber = simNumber; }
    
    public Float getBatteryLevel() { return batteryLevel; }
    public void setBatteryLevel(Float batteryLevel) { this.batteryLevel = batteryLevel; }
    
    public Integer getSignalStrength() { return signalStrength; }
    public void setSignalStrength(Integer signalStrength) { this.signalStrength = signalStrength; }
    
    public List<TrackerRawData> getRawDataEntries() { return rawDataEntries; }
    public void setRawDataEntries(List<TrackerRawData> rawDataEntries) { this.rawDataEntries = rawDataEntries; }
    
    public List<DeviceCommand> getCommands() { return commands; }
    public void setCommands(List<DeviceCommand> commands) { this.commands = commands; }
}