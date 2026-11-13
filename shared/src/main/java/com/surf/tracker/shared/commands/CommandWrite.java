package com.surf.tracker.shared.commands;

import java.time.Instant;
import java.util.UUID;

/** Already encoded wire bytes. The channel never appends delimiters or interprets commands. */
public record CommandWrite(UUID attemptId, String deviceId, String connectionId, Instant expiresAt, byte[] payload) {
    public CommandWrite {
        if (attemptId == null || expiresAt == null || deviceId == null || connectionId == null
            || !deviceId.matches("[A-Za-z0-9_.:-]{1,50}") || !connectionId.matches("[A-Za-z0-9_.:-]{1,100}")
            || payload == null || payload.length == 0 || payload.length > 4096)
            throw new IllegalArgumentException("Invalid or oversized command write");
        payload = payload.clone();
    }
    @Override public byte[] payload() { return payload.clone(); }
}
