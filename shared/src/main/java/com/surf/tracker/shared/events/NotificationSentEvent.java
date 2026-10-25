package com.surf.tracker.shared.events;

import com.surf.tracker.shared.pubsub.DomainEvent;

import java.time.Clock;
import java.util.UUID;

public class NotificationSentEvent extends DomainEvent {
    private final String notificationId;
    private final String alertId;
    private final String channel;
    private final String recipient;
    private final boolean success;
    private final String errorMessage;

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
