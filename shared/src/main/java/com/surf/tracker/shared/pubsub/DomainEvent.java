package com.surf.tracker.shared.pubsub;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "@class")
public abstract class DomainEvent {
    private final String eventId;
    private final Instant occurredOn;
    private static Supplier<UUID> uuidSupplier = UUID::randomUUID;
    private static Clock clock = Clock.systemUTC();

    // For JSON deserialization (used by Artemis/JMS)
    protected DomainEvent() {
        this(clock, uuidSupplier.get());
    }

    // Primary constructor - requires explicit dependencies
    protected DomainEvent(Clock clock, UUID eventId) {
        this.eventId = eventId.toString();
        this.occurredOn = clock.instant();
    }

    // Static setters for test configuration
    public static void setClock(Clock testClock) {
        clock = testClock;
    }

    public static void setUuidSupplier(Supplier<UUID> testUuidSupplier) {
        uuidSupplier = testUuidSupplier;
    }

    public String getEventId() { return eventId; }
    public Instant getOccurredOn() { return occurredOn; }
}