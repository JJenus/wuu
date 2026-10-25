package com.surf.tracker.shared.pubsub;

public interface EventPublisher {
    void publish(DomainEvent event);
}
