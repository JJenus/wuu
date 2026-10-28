package com.surf.tracker.core.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.surf.tracker.shared.pubsub.DomainEvent;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

public class FuelCutRequestedEvent extends DomainEvent {
    private final String vehicleId;
    private final String deviceId;

    @JsonCreator
    public FuelCutRequestedEvent(@JsonProperty("event_id") String eventId,
                                 @JsonProperty("occurred_on") Instant occurredOn,
                                 @JsonProperty("vehicle_id") String vehicleId,
                                 @JsonProperty("device_id") String deviceId) {
        super(eventId, occurredOn);
        this.vehicleId = vehicleId;
        this.deviceId = deviceId;
    }

    public FuelCutRequestedEvent(Clock clock, String vehicleId, String deviceId) {
        super(clock, UUID.randomUUID());
        this.vehicleId = vehicleId;
        this.deviceId = deviceId;
    }

    public String getVehicleId() { return vehicleId; }
    public String getDeviceId() { return deviceId; }
}
