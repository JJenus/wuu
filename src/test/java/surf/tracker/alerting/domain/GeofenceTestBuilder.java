package com.surf.tracker.alerting.domain;

import com.surf.tracker.alerting.domain.entity.Geofence;
import com.surf.tracker.alerting.domain.entity.GeofencePoint;
import com.surf.tracker.alerting.domain.enums.GeofenceShapeType;
import java.time.Instant;
import java.util.*;

public class GeofenceTestBuilder {
    private Long geofenceId;
    private String name = "Test Geofence";
    private String description;
    private GeofenceShapeType shapeType = GeofenceShapeType.CIRCLE;
    private Double centerLatitude = 40.7128;
    private Double centerLongitude = -74.0060;
    private Integer radiusMeters = 100;
    private Boolean isActive = true;
    private Set<String> vehicleIds = new HashSet<>();
    private List<GeofencePoint> points = new ArrayList<>();
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();

    private GeofenceTestBuilder() {}

    public static GeofenceTestBuilder defaultGeofence() {
        return new GeofenceTestBuilder();
    }

    public static GeofenceTestBuilder circularGeofence() {
        return new GeofenceTestBuilder()
            .name("Circular Area")
            .shapeType(GeofenceShapeType.CIRCLE)
            .centerLatitude(40.7128)
            .centerLongitude(-74.0060)
            .radiusMeters(500);
    }

    public static GeofenceTestBuilder polygonGeofence() {
        GeofenceTestBuilder builder = new GeofenceTestBuilder()
            .name("Polygon Area")
            .shapeType(GeofenceShapeType.POLYGON);

        // Add polygon points (rectangle)
        builder.points = new ArrayList<>();
        builder.points.add(createPoint(40.7128, -74.0060, 1));
        builder.points.add(createPoint(40.7128, -74.0050, 2));
        builder.points.add(createPoint(40.7118, -74.0050, 3));
        builder.points.add(createPoint(40.7118, -74.0060, 4));

        return builder;
    }

    private static GeofencePoint createPoint(Double lat, Double lon, int order) {
        GeofencePoint point = new GeofencePoint();
        point.setLatitude(lat);
        point.setLongitude(lon);
        point.setPointOrder(order);
        return point;
    }

    public GeofenceTestBuilder geofenceId(Long geofenceId) {
        this.geofenceId = geofenceId;
        return this;
    }

    public GeofenceTestBuilder name(String name) {
        this.name = name;
        return this;
    }

    public GeofenceTestBuilder shapeType(GeofenceShapeType shapeType) {
        this.shapeType = shapeType;
        return this;
    }

    public GeofenceTestBuilder centerLatitude(Double latitude) {
        this.centerLatitude = latitude;
        return this;
    }

    public GeofenceTestBuilder centerLongitude(Double longitude) {
        this.centerLongitude = longitude;
        return this;
    }

    public GeofenceTestBuilder radiusMeters(Integer radius) {
        this.radiusMeters = radius;
        return this;
    }

    public GeofenceTestBuilder active(boolean active) {
        this.isActive = active;
        return this;
    }

    public GeofenceTestBuilder vehicleId(String vehicleId) {
        this.vehicleIds.add(vehicleId);
        return this;
    }

    public GeofenceTestBuilder vehicleIds(Set<String> vehicleIds) {
        this.vehicleIds = vehicleIds;
        return this;
    }

    public Geofence build() {
        Geofence geofence = new Geofence();
        geofence.setGeofenceId(geofenceId);
        geofence.setName(name);
        geofence.setDescription(description);
        geofence.setShapeType(shapeType);
        geofence.setCenterLatitude(centerLatitude);
        geofence.setCenterLongitude(centerLongitude);
        geofence.setRadiusMeters(radiusMeters);
        geofence.setIsActive(isActive);
        geofence.setVehicleIds(vehicleIds);
        geofence.setPoints(points);
        geofence.setCreatedAt(createdAt);
        geofence.setUpdatedAt(updatedAt);
        return geofence;
    }
}
