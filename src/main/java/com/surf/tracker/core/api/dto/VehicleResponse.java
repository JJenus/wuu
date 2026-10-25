package com.surf.tracker.core.api.dto;

import java.time.Instant;

public class VehicleResponse {
    private String vehicleId;
    private String deviceId;
    private String model;
    private String licensePlate;
    private String vin;
    private String engineState;
    private Boolean accStatus;
    private Float fuelLevel;
    private Float odometerKm;
    private Boolean fuelCutActive;
    private Long organizationId;
    private Instant lastTelemetryTime;
    private LocationResponse currentLocation;

    public String getVehicleId() { return vehicleId; }
    public void setVehicleId(String vehicleId) { this.vehicleId = vehicleId; }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public String getLicensePlate() { return licensePlate; }
    public void setLicensePlate(String licensePlate) { this.licensePlate = licensePlate; }

    public String getVin() { return vin; }
    public void setVin(String vin) { this.vin = vin; }

    public String getEngineState() { return engineState; }
    public void setEngineState(String engineState) { this.engineState = engineState; }

    public Boolean getAccStatus() { return accStatus; }
    public void setAccStatus(Boolean accStatus) { this.accStatus = accStatus; }

    public Float getFuelLevel() { return fuelLevel; }
    public void setFuelLevel(Float fuelLevel) { this.fuelLevel = fuelLevel; }

    public Float getOdometerKm() { return odometerKm; }
    public void setOdometerKm(Float odometerKm) { this.odometerKm = odometerKm; }

    public Boolean getFuelCutActive() { return fuelCutActive; }
    public void setFuelCutActive(Boolean fuelCutActive) { this.fuelCutActive = fuelCutActive; }

    public Long getOrganizationId() { return organizationId; }
    public void setOrganizationId(Long organizationId) { this.organizationId = organizationId; }

    public Instant getLastTelemetryTime() { return lastTelemetryTime; }
    public void setLastTelemetryTime(Instant lastTelemetryTime) { this.lastTelemetryTime = lastTelemetryTime; }

    public LocationResponse getCurrentLocation() { return currentLocation; }
    public void setCurrentLocation(LocationResponse currentLocation) { this.currentLocation = currentLocation; }
}
