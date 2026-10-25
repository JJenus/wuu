package com.surf.tracker.devicecomm.domain;

import java.time.Instant;

public record DeviceDataPacket(
    String deviceId,
    String rawData,
    Instant receivedAt,
    String sourceIp
) {}
