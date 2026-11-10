package com.surf.tracker.alerting.api.dto;

import com.surf.tracker.alerting.domain.enums.GeofenceShapeType;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Set;

public class CreateGeofenceRequest {

    // Legacy singular input; the complete array is authoritative when present.
    @Size(min = 1, max = 50)
    private String vehicleId;

    private Set<@NotBlank @Size(max = 50) String> vehicleIds;

    @Size(max = 500)
    private String description;

    @NotEmpty(message = "Name is required")
    private String name;

    @NotNull(message = "Shape type is required")
    private GeofenceShapeType shapeType;

    private Double centerLatitude;
    private Double centerLongitude;
    private Integer radiusMeters;

    @Valid
    private List<@NotNull GeofencePointDto> points;
    private boolean active = true;
    private String createdBy;

    public String getVehicleId() {
        return vehicleId;
    }

    public Set<String> getVehicleIds() { return vehicleIds; }

    public void setVehicleIds(Set<String> vehicleIds) { this.vehicleIds = vehicleIds; }

    public String getDescription() { return description; }

    public void setDescription(String description) { this.description = description; }

    public void setVehicleId(String vehicleId) {
        this.vehicleId = vehicleId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public GeofenceShapeType getShapeType() {
        return shapeType;
    }

    public void setShapeType(GeofenceShapeType shapeType) {
        this.shapeType = shapeType;
    }

    public Double getCenterLatitude() {
        return centerLatitude;
    }

    public void setCenterLatitude(Double centerLatitude) {
        this.centerLatitude = centerLatitude;
    }

    public Double getCenterLongitude() {
        return centerLongitude;
    }

    public void setCenterLongitude(Double centerLongitude) {
        this.centerLongitude = centerLongitude;
    }

    public Integer getRadiusMeters() {
        return radiusMeters;
    }

    public void setRadiusMeters(Integer radiusMeters) {
        this.radiusMeters = radiusMeters;
    }

    public List<GeofencePointDto> getPoints() {
        return points;
    }

    public void setPoints(List<GeofencePointDto> points) {
        this.points = points;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }
}
