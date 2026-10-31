package com.surf.tracker.core.api.dto;

import jakarta.validation.constraints.Size;

public class UpdateVehicleRequest {
    @Size(max = 100, message = "Model cannot exceed 100 characters")
    private String model;

    @Size(max = 20, message = "License plate cannot exceed 20 characters")
    private String licensePlate;

    @Size(max = 17, message = "VIN cannot exceed 17 characters")
    private String vin;

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public String getLicensePlate() { return licensePlate; }
    public void setLicensePlate(String licensePlate) { this.licensePlate = licensePlate; }

    public String getVin() { return vin; }
    public void setVin(String vin) { this.vin = vin; }
}
