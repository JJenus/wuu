package com.surf.tracker.devicecomm.protocol;

import java.time.Instant;
import java.util.Set;

/** Encodes protocol-neutral commands into exact wire bytes. */
public interface CommandCodec {
    /** For protocols that cannot be commanded (for example a phone). */
    CommandCodec NONE = new CommandCodec() {
        @Override
        public Set<DeviceCommand> supported() {
            return Set.of();
        }

        @Override
        public byte[] encode(DeviceCommand command, String deviceId, Instant at) {
            throw com.surf.tracker.devicecomm.exception.ProtocolException.unsupportedCommand("protocol", command.name());
        }
    };

    Set<DeviceCommand> supported();

    /**
     * @param at command timestamp for protocols that embed one; passed in so encoding is deterministic
     * @throws com.surf.tracker.devicecomm.exception.ProtocolException if the command or device id is not encodable
     */
    byte[] encode(DeviceCommand command, String deviceId, Instant at);
}
