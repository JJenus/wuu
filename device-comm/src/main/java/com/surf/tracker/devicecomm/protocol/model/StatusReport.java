package com.surf.tracker.devicecomm.protocol.model;

import com.surf.tracker.shared.domain.DeviceHealth;
import java.time.Instant;

/** A heartbeat or other frame proving the device is alive, with no GPS fix. */
public record StatusReport(String deviceId, Instant receivedAt, DeviceHealth health) implements DeviceMessage {
}
