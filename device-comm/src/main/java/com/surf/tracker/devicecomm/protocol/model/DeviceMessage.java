package com.surf.tracker.devicecomm.protocol.model;

import java.time.Instant;

/**
 * What a protocol decodes a frame into. Sealed so the pipeline's routing switch is exhaustive:
 * a new message kind cannot be added without deciding what happens to it.
 */
public sealed interface DeviceMessage permits PositionReport, StatusReport, LoginReport {
    String deviceId();

    Instant receivedAt();
}
