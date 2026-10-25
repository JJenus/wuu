package com.surf.tracker.notification.application.service;

import com.surf.tracker.notification.domain.entity.Delivery;
import com.surf.tracker.notification.domain.enums.ErrorType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class PushNotificationService implements NotificationService {

    private static final Logger logger = LoggerFactory.getLogger(PushNotificationService.class);

    @Value("${notification.push.enabled:false}")
    private boolean enabled;

    @Value("${notification.push.provider:none}")
    private String provider;

    @Override
    public DeliveryResult send(Delivery delivery) {
        if (!enabled) {
            logger.warn("Push notifications are disabled");
            return DeliveryResult.failure("Push notifications are disabled", ErrorType.PERMANENT);
        }

        try {
            logger.info("Push notification would be sent to: {} via {}",
                delivery.getRecipient(), provider);
            delivery.markSent();
            return DeliveryResult.success();

        } catch (Exception e) {
            logger.error("Failed to send push notification", e);
            return classifyError(e);
        }
    }

    private DeliveryResult classifyError(Exception e) {
        String msg = e.getMessage() != null ? e.getMessage() : "";
        if (msg.contains("InvalidRegistration") || msg.contains("NotRegistered")) {
            return DeliveryResult.failure(e.getMessage(), ErrorType.PERMANENT);
        }
        if (msg.contains("Unavailable") || msg.contains("InternalServerError")
            || msg.contains("rate limit") || msg.contains("timeout")) {
            return DeliveryResult.failure(e.getMessage(), ErrorType.TRANSIENT);
        }
        return DeliveryResult.failure(e.getMessage(), ErrorType.TRANSIENT);
    }

    @Override
    public boolean isAvailable() {
        return enabled && !"none".equals(provider);
    }

    @Override
    public String getChannel() {
        return "MOBILE_PUSH";
    }
}