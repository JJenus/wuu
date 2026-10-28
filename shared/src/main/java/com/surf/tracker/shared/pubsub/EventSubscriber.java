package com.surf.tracker.shared.pubsub;

public interface EventSubscriber {
    <T extends DomainEvent> void subscribe(Class<T> eventType, EventHandler<T> handler);
    void unsubscribe(Class<? extends DomainEvent> eventType, EventHandler<?> handler);
}
