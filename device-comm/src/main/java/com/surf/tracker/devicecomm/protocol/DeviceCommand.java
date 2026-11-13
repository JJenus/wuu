package com.surf.tracker.devicecomm.protocol;

/** Protocol-neutral commands. Each protocol's {@link CommandCodec} says which it can encode. */
public enum DeviceCommand {
    FUEL_CUT,
    ENGINE_RESTORE
}
