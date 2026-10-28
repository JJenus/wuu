package com.surf.tracker.shared.events;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.surf.tracker.shared.pubsub.DomainEvent;
import com.surf.tracker.shared.domain.LocationPoint;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public class VehicleUpdatedEvent extends DomainEvent {
    private final String vehicleId;
    private final Long organizationId;
    private final LocationPoint newLocation;
    private final Map<String, Object> metaData;

    @JsonCreator
    public VehicleUpdatedEvent(@JsonProperty("event_id") String eventId,
                               @JsonProperty("occurred_on") Instant occurredOn,
                               @JsonProperty("vehicle_id") String vehicleId,
                               @JsonProperty("organization_id") Long organizationId,
                               @JsonProperty("new_location") LocationPoint newLocation,
                               @JsonProperty("meta_data") Map<String, Object> metaData) {
        super(eventId, occurredOn);
        this.vehicleId = vehicleId;
        this.organizationId = organizationId;
        this.newLocation = newLocation;
        this.metaData = metaData;
    }

    public VehicleUpdatedEvent(Clock clock, UUID eventId, String vehicleId,
                               LocationPoint newLocation, Map<String, Object> metaData) {
        this(clock, eventId, vehicleId, null, newLocation, metaData);
    }

    public VehicleUpdatedEvent(Clock clock, UUID eventId, String vehicleId, Long organizationId,
                               LocationPoint newLocation, Map<String, Object> metaData) {
        super(clock, eventId);
        this.vehicleId = vehicleId;
        this.organizationId = organizationId;
        this.newLocation = newLocation;
        this.metaData = metaData;
    }

    public String getVehicleId() {
        return vehicleId;
    }
    public Long getOrganizationId() { return organizationId; }
    public Map<String, Object> getMetaData() { return metaData; }
    public LocationPoint getNewLocation() { return newLocation; }
}
