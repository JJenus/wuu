package com.surf.tracker.devicecomm.protocol;

/**
 * The listener a protocol is served on. Each transport has its own registry and pipeline, because
 * framing must be shared by every protocol on one listener and authentication differs per transport.
 */
public enum Transport {
    /** Raw device TCP stream; identity is whatever the device's own frames claim. */
    TCP,
    /** Authenticated WebSocket; identity comes from a verified pairing token, not the payload. */
    WEBSOCKET
}
