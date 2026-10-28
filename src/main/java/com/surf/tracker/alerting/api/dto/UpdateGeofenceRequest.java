package com.surf.tracker.alerting.api.dto;

import com.surf.tracker.alerting.domain.enums.GeofenceShapeType;
import jakarta.validation.constraints.Size;
import java.util.Set;

public class UpdateGeofenceRequest {

    @Size(min = 1, max = 100, message = "Name must be between 1 and 100 characters")
    private String name;

    @Size(max = 500, message = "Description cannot exceed 500 characters")
    private String description;

    private GeofenceShapeType shapeType;

    private Double centerLatitude;
    private Double centerLongitude;
    private Integer radiusMeters;

    private Boolean active;

    private Set<String> vehicleIds;

    // Getters and Setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public GeofenceShapeType getShapeType() { return shapeType; }
    public void setShapeType(GeofenceShapeType shapeType) { this.shapeType = shapeType; }

    public Double getCenterLatitude() { return centerLatitude; }
    public void setCenterLatitude(Double centerLatitude) { this.centerLatitude = centerLatitude; }

    public Double getCenterLongitude() { return centerLongitude; }
    public void setCenterLongitude(Double centerLongitude) { this.centerLongitude = centerLongitude; }

    public Integer getRadiusMeters() { return radiusMeters; }
    public void setRadiusMeters(Integer radiusMeters) { this.radiusMeters = radiusMeters; }

    public Boolean isActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }

    public Set<String> getVehicleIds() { return vehicleIds; }
    public void setVehicleIds(Set<String> vehicleIds) { this.vehicleIds = vehicleIds; }
}