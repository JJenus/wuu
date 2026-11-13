package com.surf.tracker.devicecomm.protocol.hq;

/** A GPS fix field is malformed. Messages are static: they never contain device payload. */
public final class HqFixException extends RuntimeException {
    public HqFixException(String message) {
        super(message);
    }
}
