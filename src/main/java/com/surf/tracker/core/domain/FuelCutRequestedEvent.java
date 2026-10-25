package com.surf.tracker.core.domain;

import com.surf.tracker.shared.pubsub.DomainEvent;

import java.time.Clock;
import java.util.UUID;

public class FuelCutRequestedEvent extends DomainEvent {
    private final String vehicleId;
    private final String deviceId;

    public FuelCutRequestedEvent(Clock clock, String vehicleId, String deviceId) {
        super(clock, UUID.randomUUID());
        this.vehicleId = vehicleId;
        this.deviceId = deviceId;
    }

    public String getVehicleId() { return vehicleId; }
    public String getDeviceId() { return deviceId; }
}
