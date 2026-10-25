package com.surf.tracker.shared.pubsub;

@FunctionalInterface
public interface EventHandler<T extends DomainEvent> {
    void handle(T event);
}
