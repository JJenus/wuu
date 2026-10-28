package com.surf.tracker.alerting.domain.enums;

public enum AlertType {
    // Speed related
    OVERSPEED("Overspeed violation"),
    RAPID_ACCELERATION("Rapid acceleration"),
    HARD_BRAKING("Hard braking"),
    
    // Geofence related
    GEOFENCE_ENTRY("Geofence entry"),
    GEOFENCE_EXIT("Geofence exit"),
    GEOFENCE_VIOLATION("Geofence violation"),
    GEOFENCE_DWELL_EXCEEDED("Geofence dwell time exceeded"),
    
    // Time related
    IDLE_TIMEOUT("Excessive idling"),
    LONG_STOP("Long stop detected"),
    SCHEDULE_VIOLATION("Schedule violation"),
    
    // Device status
    DEVICE_DISCONNECTED("Device disconnected"),
    LOW_BATTERY("Low battery"),
    NO_GPS_SIGNAL("No GPS signal"),
    TAMPER_DETECTED("Device tampering detected"),
    
    // Vehicle status
    ENGINE_ON_OUTSIDE_HOURS("Engine on outside permitted hours"),
    FUEL_THEFT_SUSPECTED("Fuel theft suspected"),
    MAINTENANCE_DUE("Maintenance due"),
    
    // Safety
    ACCIDENT_DETECTED("Possible accident detected"),
    PANIC_BUTTON_PRESSED("Panic button activated"),
    
    // Business rules
    ROUTE_DEVIATION("Route deviation"),
    UNAUTHORIZED_AREA("Entered unauthorized area"),
    HIJACKING_SUSPECTED("Possible hijacking"),

    UNKNOWN("Unknowm alert");
    
    private final String description;
    
    AlertType(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
}