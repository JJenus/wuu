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

        // No push provider is implemented; configuration alone cannot establish delivery.
        return DeliveryResult.failure("Unsupported push provider: " + provider, ErrorType.PERMANENT);
    }

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public String getChannel() {
        return "MOBILE_PUSH";
    }
}
