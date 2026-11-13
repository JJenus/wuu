package com.surf.tracker.devicecomm.pipeline;

import com.surf.tracker.devicecomm.exception.ProtocolException;
import com.surf.tracker.devicecomm.protocol.DeviceProtocol;

/**
 * Per-connection state: the protocol detected on the first valid frame and the device identity the
 * connection has proven. Protocol implementations stay stateless; this is where state lives.
 */
public final class ConnectionSession {
    private volatile DeviceProtocol protocol;
    private volatile String boundDeviceId;

    /** Null until a frame has been accepted. */
    public DeviceProtocol protocol() {
        return protocol;
    }

    /** The device id this connection has proven, or null before the first accepted frame. */
    public String boundDeviceId() {
        return boundDeviceId;
    }

    /** A connection cannot start speaking as a different device. */
    void requireIdentity(String deviceId) {
        String bound = boundDeviceId;
        if (bound != null && !bound.equals(deviceId))
            throw new ProtocolException("DEVICE_ID_CHANGED", "A connection cannot change device identity");
    }

    void accept(DeviceProtocol accepted, String deviceId) {
        if (protocol == null) protocol = accepted;
        if (boundDeviceId == null) boundDeviceId = deviceId;
    }
}
