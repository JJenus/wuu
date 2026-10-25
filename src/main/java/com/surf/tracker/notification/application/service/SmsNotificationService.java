package com.surf.tracker.notification.application.service;

import com.surf.tracker.notification.domain.entity.Delivery;
import com.surf.tracker.notification.domain.enums.ErrorType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SmsNotificationService implements NotificationService {

    private static final Logger logger = LoggerFactory.getLogger(SmsNotificationService.class);

    @Value("${notification.sms.enabled:false}")
    private boolean enabled;

    @Value("${notification.sms.provider:none}")
    private String provider;

    @Override
    public DeliveryResult send(Delivery delivery) {
        if (!enabled) {
            logger.warn("SMS notifications are disabled");
            return DeliveryResult.failure("SMS notifications are disabled", ErrorType.PERMANENT);
        }

        try {
            logger.info("SMS would be sent to: {} via {}",
                delivery.getRecipient(), provider);
            delivery.markSent();
            return DeliveryResult.success();

        } catch (Exception e) {
            logger.error("Failed to send SMS notification", e);
            return DeliveryResult.failure(e.getMessage(), ErrorType.TRANSIENT);
        }
    }

    @Override
    public boolean isAvailable() {
        return enabled && !"none".equals(provider);
    }

    @Override
    public String getChannel() {
        return "SMS";
    }
}