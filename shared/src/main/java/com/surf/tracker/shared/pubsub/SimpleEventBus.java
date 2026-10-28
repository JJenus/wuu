package com.surf.tracker.shared.pubsub;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

public class SimpleEventBus implements EventPublisher, EventSubscriber {
    private static final Logger log = LoggerFactory.getLogger(SimpleEventBus.class);
    private final ConcurrentHashMap<Class<?>, CopyOnWriteArrayList<EventHandler<?>>> handlers;
    private final ExecutorService executor;

    public SimpleEventBus() {
        this.handlers = new ConcurrentHashMap<>();
        this.executor = Executors.newFixedThreadPool(10);
    }

    @Override
    public void publish(DomainEvent event) {
        if (event == null) {
            return;
        }

        Class<?> eventType = event.getClass();
        CopyOnWriteArrayList<EventHandler<?>> eventHandlers = handlers.get(eventType);

        if (eventHandlers != null && !eventHandlers.isEmpty()) {
            for (EventHandler handler : eventHandlers) {
                try {
                    executor.submit(() -> {
                        try {
                            handler.handle(event);
                        } catch (Exception e) {
                            log.error("Error handling event", e);
                        }
                    });
                } catch (RejectedExecutionException e) {
                    log.warn("Event rejected from executor", e);
                }
            }
        }
    }

    @Override
    public <T extends DomainEvent> void subscribe(Class<T> eventType, EventHandler<T> handler) {
        if (eventType == null || handler == null) {
            return;
        }
        handlers.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>()).add(handler);
    }

    @Override
    public void unsubscribe(Class<? extends DomainEvent> eventType, EventHandler<?> handler) {
        if (eventType == null || handler == null) {
            return;
        }
        CopyOnWriteArrayList<EventHandler<?>> eventHandlers = handlers.get(eventType);
        if (eventHandlers != null) {
            eventHandlers.remove(handler);
        }
    }

    public void shutdown() {
        executor.shutdown();
    }
}
