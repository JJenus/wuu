package com.surf.tracker.shared.events;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.surf.tracker.shared.pubsub.DomainEvent;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

public class NotificationSentEvent extends DomainEvent {
    private final String notificationId;
    private final String alertId;
    private final String channel;
    private final String recipient;
    private final boolean success;
    private final String errorMessage;

    @JsonCreator
    public NotificationSentEvent(@JsonProperty("event_id") String eventId,
                                 @JsonProperty("occurred_on") Instant occurredOn,
                                 @JsonProperty("notification_id") String notificationId,
                                 @JsonProperty("alert_id") String alertId,
                                 @JsonProperty("channel") String channel,
                                 @JsonProperty("recipient") String recipient,
                                 @JsonProperty("success") boolean success,
                                 @JsonProperty("error_message") String errorMessage) {
        super(eventId, occurredOn);
        this.notificationId = notificationId;
        this.alertId = alertId;
        this.channel = channel;
        this.recipient = recipient;
        this.success = success;
        this.errorMessage = errorMessage;
    }

    public NotificationSentEvent(
        Clock clock,
        String notificationId,
        String alertId,
        String channel,
        String recipient,
        boolean success,
        String errorMessage
    ) {
        super(clock, UUID.randomUUID());
        this.notificationId = notificationId;
        this.alertId = alertId;
        this.channel = channel;
        this.recipient = recipient;
        this.success = success;
        this.errorMessage = errorMessage;
    }

    public String getNotificationId() { return notificationId; }
    public String getAlertId() { return alertId; }
    public String getChannel() { return channel; }
    public String getRecipient() { return recipient; }
    public boolean isSuccess() { return success; }
    public String getErrorMessage() { return errorMessage; }
}
