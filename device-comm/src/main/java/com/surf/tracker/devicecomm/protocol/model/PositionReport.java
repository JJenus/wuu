package com.surf.tracker.devicecomm.protocol.model;

import com.surf.tracker.shared.domain.DeviceHealth;
import com.surf.tracker.shared.domain.LocationPoint;

import java.time.Instant;

/** A frame carrying a valid GPS fix. */
public record PositionReport(String deviceId, Instant receivedAt, LocationPoint point, DeviceHealth health)
        implements DeviceMessage {
}
