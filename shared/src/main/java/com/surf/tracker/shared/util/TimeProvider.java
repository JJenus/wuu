package com.surf.tracker.shared.util;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Supplier;

public final class TimeProvider {
    private static volatile Clock clock = Clock.systemUTC();
    private static volatile Supplier<UUID> uuidSupplier = UUID::randomUUID;

    private TimeProvider() {}

    public static Instant now() {
        return clock.instant();
    }

    public static String newId() {
        return uuidSupplier.get().toString();
    }

    public static UUID newUuid() {
        return uuidSupplier.get();
    }

    public static void setClock(Clock testClock) {
        clock = testClock;
    }

    public static void setUuidSupplier(Supplier<UUID> testUuidSupplier) {
        uuidSupplier = testUuidSupplier;
    }

    public static void reset() {
        clock = Clock.systemUTC();
        uuidSupplier = UUID::randomUUID;
    }
}
