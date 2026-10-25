package com.surf.tracker.shared.events;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.surf.tracker.shared.pubsub.DomainEvent;
import com.surf.tracker.shared.domain.LocationPoint;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;

public class VehicleUpdatedEvent extends DomainEvent {
    private final String vehicleId;
    private final LocationPoint newLocation;
    private final Map<String, Object> metaData;

    public VehicleUpdatedEvent(Clock clock, UUID eventId, String vehicleId,
                               LocationPoint newLocation, Map<String, Object> metaData) {
        super(clock, eventId);
        this.vehicleId = vehicleId;
        this.newLocation = newLocation;
        this.metaData = metaData;
    }

    public String getVehicleId() {
        return vehicleId;
    }
    public Map<String, Object> getMetaData() { return metaData; }
    public LocationPoint getNewLocation() { return newLocation; }
}
