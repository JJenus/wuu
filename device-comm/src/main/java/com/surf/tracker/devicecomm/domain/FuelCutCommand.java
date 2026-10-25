package com.surf.tracker.devicecomm.domain;

import com.surf.tracker.shared.pubsub.DomainEvent;

import java.time.Clock;
import java.util.UUID;

public class FuelCutCommand extends DomainEvent {
    private final String vehicleId;
    private final String deviceId;

    public FuelCutCommand(Clock clock, String vehicleId, String deviceId) {
        super(clock, UUID.randomUUID());
        this.vehicleId = vehicleId;
        this.deviceId = deviceId;
    }

    public FuelCutCommand(String vehicleId, String deviceId) {
        this(Clock.systemUTC(), vehicleId, deviceId);
    }

    public String getVehicleId() { return vehicleId; }
    public String getDeviceId() { return deviceId; }
}
