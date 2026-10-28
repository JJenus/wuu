package com.surf.tracker.notification.application.service;

import com.surf.tracker.notification.domain.entity.Delivery;
import com.surf.tracker.notification.domain.enums.ErrorType;
import com.surf.tracker.notification.infrastructure.websocket.VehicleTrackingWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class WebSocketNotificationService implements NotificationService {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketNotificationService.class);

    private final VehicleTrackingWebSocketHandler webSocketHandler;

    public WebSocketNotificationService(VehicleTrackingWebSocketHandler webSocketHandler) {
        this.webSocketHandler = webSocketHandler;
    }

    @Override
    public DeliveryResult send(Delivery delivery) {
        try {
            if (!webSocketHandler.isUserConnected(delivery.getRecipient())) {
                return DeliveryResult.failure("User not connected via WebSocket", ErrorType.TRANSIENT);
            }

            boolean sent = webSocketHandler.sendAlertNotification(
                delivery.getRecipient(),
                delivery.getNotificationHub() == null ? null : delivery.getNotificationHub().getTenantId(),
                createWebSocketMessage(delivery)
            );
            if (!sent) return DeliveryResult.failure("No authorized connection", ErrorType.TRANSIENT);

            delivery.markSent();
            logger.debug("WebSocket notification sent to user: {}", delivery.getRecipient());
            return DeliveryResult.success();

        } catch (Exception e) {
            logger.error("Failed to send WebSocket notification", e);
            return DeliveryResult.failure(e.getMessage(), ErrorType.TRANSIENT);
        }
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public String getChannel() {
        return "WEBSOCKET";
    }

    private Object createWebSocketMessage(Delivery delivery) {
        return new WebSocketNotificationMessage(
            delivery.getDeliveryId(),
            delivery.getNotificationHub() != null ? delivery.getNotificationHub().getAlertId() : null,
            delivery.getTitle(),
            delivery.getMessage(),
            delivery.getChannel().name(),
            System.currentTimeMillis()
        );
    }

    private static class WebSocketNotificationMessage {
        private final String deliveryId;
        private final String alertId;
        private final String title;
        private final String message;
        private final String channel;
        private final long timestamp;

        public WebSocketNotificationMessage(String deliveryId, String alertId, String title,
                                            String message, String channel, long timestamp) {
            this.deliveryId = deliveryId;
            this.alertId = alertId;
            this.title = title;
            this.message = message;
            this.channel = channel;
            this.timestamp = timestamp;
        }

        public String getDeliveryId() { return deliveryId; }
        public String getAlertId() { return alertId; }
        public String getTitle() { return title; }
        public String getMessage() { return message; }
        public String getChannel() { return channel; }
        public long getTimestamp() { return timestamp; }
    }
}
