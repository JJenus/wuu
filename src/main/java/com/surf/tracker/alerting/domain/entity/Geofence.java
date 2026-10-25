package com.surf.tracker.alerting.domain.entity;

import com.surf.tracker.alerting.domain.enums.GeofenceShapeType;
import com.surf.tracker.shared.util.TimeProvider;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "geofences")
public class Geofence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "geofence_id")
    private Long geofenceId;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "shape_type", length = 20)
    private GeofenceShapeType shapeType;

    @Column(name = "center_latitude")
    private Double centerLatitude;

    @Column(name = "center_longitude")
    private Double centerLongitude;

    @Column(name = "radius_meters")
    private Integer radiusMeters;

    @Column(name = "is_active")
    private Boolean isActive = true;

    // Many-to-many with vehicles (through vehicle IDs)
    @ElementCollection
    @CollectionTable(
            name = "geofence_vehicles",
            joinColumns = @JoinColumn(name = "geofence_id"),
            indexes = @Index(name = "idx_geofence_vehicles_vehicle_id", columnList = "vehicle_id")
    )
    @Column(name = "vehicle_id", length = 50)
    private Set<String> vehicleIds = new HashSet<>();

    @OneToMany(mappedBy = "geofence", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("pointOrder ASC")
    private List<GeofencePoint> points = new ArrayList<>();

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "organization_id")
    private Long organizationId;

    @Column(name = "created_at")
    private Instant createdAt = Instant.EPOCH;

    @Column(name = "updated_at")
    private Instant updatedAt = Instant.EPOCH;

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = TimeProvider.now();
    }

    // Business methods
    public void addPoint(Double latitude, Double longitude, Integer order) {
        GeofencePoint point = new GeofencePoint();
        point.setGeofence(this);
        point.setLatitude(latitude);
        point.setLongitude(longitude);
        point.setPointOrder(order);
        points.add(point);
    }

    public boolean isPointInside(Double latitude, Double longitude) {
        if (shapeType == GeofenceShapeType.CIRCLE) {
            return isPointInCircle(latitude, longitude);
        } else if (shapeType == GeofenceShapeType.POLYGON) {
            return isPointInPolygon(latitude, longitude);
        }
        return false;
    }

    private boolean isPointInCircle(Double latitude, Double longitude) {
        if (centerLatitude == null || centerLongitude == null || radiusMeters == null) {
            return false;
        }

        double distance = calculateDistance(centerLatitude, centerLongitude, latitude, longitude);
        return distance <= radiusMeters;
    }

    private boolean isPointInPolygon(Double latitude, Double longitude) {
        if (points.size() < 3) {
            return false;
        }

        // Implement ray casting algorithm for polygon
        boolean inside = false;
        for (int i = 0, j = points.size() - 1; i < points.size(); j = i++) {
            Double xi = points.get(i).getLatitude();
            Double yi = points.get(i).getLongitude();
            Double xj = points.get(j).getLatitude();
            Double yj = points.get(j).getLongitude();

            boolean intersect = ((yi > longitude) != (yj > longitude))
                    && (latitude < (xj - xi) * (longitude - yi) / (yj - yi) + xi);
            if (intersect) inside = !inside;
        }
        return inside;
    }

    private double calculateDistance(Double lat1, Double lon1, Double lat2, Double lon2) {
        double earthRadiusM = 6371000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1))
                * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadiusM * c;
    }

    // Vehicle management methods
    public void addVehicle(String vehicleId) {
        if (vehicleIds == null) {
            vehicleIds = new HashSet<>();
        }
        vehicleIds.add(vehicleId);
    }

    public void removeVehicle(String vehicleId) {
        if (vehicleIds != null) {
            vehicleIds.remove(vehicleId);
        }
    }

    public boolean hasVehicle(String vehicleId) {
        return vehicleIds != null && vehicleIds.contains(vehicleId);
    }

    // Getters and Setters
    public Long getGeofenceId() { return geofenceId; }
    public void setGeofenceId(Long geofenceId) { this.geofenceId = geofenceId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public GeofenceShapeType getShapeType() { return shapeType; }
    public void setShapeType(GeofenceShapeType shapeType) { this.shapeType = shapeType; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public Set<String> getVehicleIds() { return vehicleIds; }
    public void setVehicleIds(Set<String> vehicleIds) { this.vehicleIds = vehicleIds; }

    public List<GeofencePoint> getPoints() { return points; }
    public void setPoints(List<GeofencePoint> points) { this.points = points; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    // Additional getters/setters
    public Double getCenterLatitude() { return centerLatitude; }
    public void setCenterLatitude(Double centerLatitude) { this.centerLatitude = centerLatitude; }

    public Double getCenterLongitude() { return centerLongitude; }
    public void setCenterLongitude(Double centerLongitude) { this.centerLongitude = centerLongitude; }

    public Integer getRadiusMeters() { return radiusMeters; }
    public void setRadiusMeters(Integer radiusMeters) { this.radiusMeters = radiusMeters; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public Long getOrganizationId() { return organizationId; }
    public void setOrganizationId(Long organizationId) { this.organizationId = organizationId; }
}