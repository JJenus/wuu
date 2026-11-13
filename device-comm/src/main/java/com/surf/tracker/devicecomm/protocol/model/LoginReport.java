package com.surf.tracker.devicecomm.protocol.model;

import java.time.Instant;

/** A device announcing itself on a new connection. */
public record LoginReport(String deviceId, Instant receivedAt) implements DeviceMessage {
}
