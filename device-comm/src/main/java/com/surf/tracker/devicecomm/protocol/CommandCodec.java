package com.surf.tracker.devicecomm.protocol;

import java.time.Instant;
import java.util.Set;

/** Encodes protocol-neutral commands into exact wire bytes. */
public interface CommandCodec {
    Set<DeviceCommand> supported();

    /**
     * @param at command timestamp for protocols that embed one; passed in so encoding is deterministic
     * @throws com.surf.tracker.devicecomm.exception.ProtocolException if the command or device id is not encodable
     */
    byte[] encode(DeviceCommand command, String deviceId, Instant at);
}
