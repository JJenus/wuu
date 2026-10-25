package com.surf.tracker.notification.domain.entity;

import com.surf.tracker.notification.domain.enums.DeliveryEventType;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "delivery_events", indexes = {
    @Index(name = "idx_event_delivery_id", columnList = "deliveryId"),
    @Index(name = "idx_event_occurred_at", columnList = "occurredAt DESC")
})
public class DeliveryEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String deliveryId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeliveryEventType eventType;

    @Column(columnDefinition = "TEXT")
    private String metadata;

    @Column(nullable = false)
    private Instant occurredAt;

    private DeliveryEvent() {
    }

    public DeliveryEvent(String deliveryId, DeliveryEventType eventType, String metadata, Instant occurredAt) {
        this.deliveryId = deliveryId;
        this.eventType = eventType;
        this.metadata = metadata;
        this.occurredAt = occurredAt;
    }

    public static DeliveryEvent created(String deliveryId, String metadata, Instant occurredAt) {
        return new DeliveryEvent(deliveryId, DeliveryEventType.CREATED, metadata, occurredAt);
    }

    public static DeliveryEvent sent(String deliveryId, String metadata, Instant occurredAt) {
        return new DeliveryEvent(deliveryId, DeliveryEventType.SENT, metadata, occurredAt);
    }

    public static DeliveryEvent delivered(String deliveryId, String metadata, Instant occurredAt) {
        return new DeliveryEvent(deliveryId, DeliveryEventType.DELIVERED, metadata, occurredAt);
    }

    public static DeliveryEvent opened(String deliveryId, String metadata, Instant occurredAt) {
        return new DeliveryEvent(deliveryId, DeliveryEventType.OPENED, metadata, occurredAt);
    }

    public static DeliveryEvent clicked(String deliveryId, String metadata, Instant occurredAt) {
        return new DeliveryEvent(deliveryId, DeliveryEventType.CLICKED, metadata, occurredAt);
    }

    public static DeliveryEvent failed(String deliveryId, String error, Instant occurredAt) {
        return new DeliveryEvent(deliveryId, DeliveryEventType.FAILED, error, occurredAt);
    }

    public static DeliveryEvent retryScheduled(String deliveryId, String metadata, Instant occurredAt) {
        return new DeliveryEvent(deliveryId, DeliveryEventType.RETRY_SCHEDULED, metadata, occurredAt);
    }

    public static DeliveryEvent exhausted(String deliveryId, String metadata, Instant occurredAt) {
        return new DeliveryEvent(deliveryId, DeliveryEventType.EXHAUSTED, metadata, occurredAt);
    }

    public Long getId() { return id; }
    public String getDeliveryId() { return deliveryId; }
    public DeliveryEventType getEventType() { return eventType; }
    public String getMetadata() { return metadata; }
    public Instant getOccurredAt() { return occurredAt; }
}
