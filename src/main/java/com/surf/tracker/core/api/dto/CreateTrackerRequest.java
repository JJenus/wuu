package com.surf.tracker.core.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CreateTrackerRequest {
    @NotBlank(message = "Tracker ID is required")
    @Size(max = 50, message = "Tracker ID cannot exceed 50 characters")
    private String trackerId;

    @NotBlank(message = "Device ID is required")
    @Size(max = 50, message = "Device ID cannot exceed 50 characters")
    private String deviceId;

    @NotBlank(message = "Vehicle ID is required")
    @Size(max = 50, message = "Vehicle ID cannot exceed 50 characters")
    private String vehicleId;

    @Size(max = 100, message = "Model cannot exceed 100 characters")
    private String model;

    @Size(max = 20, message = "Protocol cannot exceed 20 characters")
    private String protocol;

    @Size(max = 50, message = "Firmware version cannot exceed 50 characters")
    private String firmwareVersion;

    @Size(max = 20, message = "SIM number cannot exceed 20 characters")
    private String simNumber;

    public String getTrackerId() { return trackerId; }
    public void setTrackerId(String trackerId) { this.trackerId = trackerId; }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public String getVehicleId() { return vehicleId; }
    public void setVehicleId(String vehicleId) { this.vehicleId = vehicleId; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public String getProtocol() { return protocol; }
    public void setProtocol(String protocol) { this.protocol = protocol; }

    public String getFirmwareVersion() { return firmwareVersion; }
    public void setFirmwareVersion(String firmwareVersion) { this.firmwareVersion = firmwareVersion; }

    public String getSimNumber() { return simNumber; }
    public void setSimNumber(String simNumber) { this.simNumber = simNumber; }
}
