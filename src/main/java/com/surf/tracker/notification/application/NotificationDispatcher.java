package com.surf.tracker.notification.application;

import com.surf.tracker.notification.application.service.*;
import com.surf.tracker.notification.domain.entity.Delivery;
import com.surf.tracker.notification.domain.entity.DeliveryEvent;
import com.surf.tracker.notification.domain.enums.DeliveryEventType;
import com.surf.tracker.notification.domain.enums.DeliveryStatus;
import com.surf.tracker.notification.domain.enums.NotificationChannel;
import com.surf.tracker.notification.domain.enums.ErrorType;
import com.surf.tracker.notification.infrastructure.repository.DeliveryEventRepository;
import com.surf.tracker.notification.infrastructure.repository.DeliveryRepository;
import com.surf.tracker.notification.infrastructure.repository.DeviceRepository;
import com.surf.tracker.notification.application.scheduler.DeliveryBackoff;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class NotificationDispatcher {

    private static final Logger logger = LoggerFactory.getLogger(NotificationDispatcher.class);

    private final Map<NotificationChannel, NotificationService> serviceMap;
    private final DeliveryRepository deliveryRepository;
    private final DeliveryEventRepository eventRepository;
    private final DeviceRepository deviceRepository;
    private final Clock clock;

    public NotificationDispatcher(
        WebSocketNotificationService webSocketService,
        EmailNotificationService emailService,
        SmsNotificationService smsService,
        PushNotificationService pushService,
        DeliveryRepository deliveryRepository,
        DeliveryEventRepository eventRepository,
        DeviceRepository deviceRepository,
        Clock clock
    ) {
        this.serviceMap = new ConcurrentHashMap<>();
        this.deliveryRepository = deliveryRepository;
        this.eventRepository = eventRepository;
        this.deviceRepository = deviceRepository;
        this.clock = clock;

        serviceMap.put(NotificationChannel.WEBSOCKET, webSocketService);
        serviceMap.put(NotificationChannel.EMAIL, emailService);
        serviceMap.put(NotificationChannel.SMS, smsService);
        serviceMap.put(NotificationChannel.MOBILE_PUSH, pushService);
        serviceMap.put(NotificationChannel.IN_APP, webSocketService);
    }

    @Transactional
    public void dispatch(Delivery delivery) {
        delivery.setStatus(DeliveryStatus.SENDING);
        deliveryRepository.save(delivery);

        NotificationService service = serviceMap.get(delivery.getChannel());

        if (service == null) {
            logger.error("No service registered for channel: {}", delivery.getChannel());
            handleFailure(delivery, "No service available for channel: " + delivery.getChannel(), ErrorType.PERMANENT);
            return;
        }

        DeliveryResult result = service.send(delivery);

        if (result.ok()) {
            handleSuccess(delivery);
        } else {
            handleFailure(delivery, result.error(), result.errorType());
        }
    }

    @Transactional
    public void redispatch(Delivery delivery) {
        if (!delivery.canRetry()) {
            logger.warn("Delivery {} cannot be retried", delivery.getDeliveryId());
            return;
        }

        delivery.incrementAttemptCount();
        delivery.setStatus(DeliveryStatus.SENDING);
        deliveryRepository.save(delivery);

        NotificationService service = serviceMap.get(delivery.getChannel());

        if (service == null) {
            handleFailure(delivery, "No service available for channel: " + delivery.getChannel(), ErrorType.PERMANENT);
            return;
        }

        DeliveryResult result = service.send(delivery);

        if (result.ok()) {
            handleSuccess(delivery);
        } else {
            handleFailure(delivery, result.error(), result.errorType());
        }
    }

    private void handleSuccess(Delivery delivery) {
        Instant now = clock.instant();
        delivery.setStatus(DeliveryStatus.SENT);
        delivery.setSentAt(now);
        delivery.setNextRetryAt(null);
        deliveryRepository.save(delivery);

        DeliveryEvent event = DeliveryEvent.sent(delivery.getDeliveryId(), null, now);
        eventRepository.save(event);

        logger.info("Successfully dispatched delivery {} via {}",
            delivery.getDeliveryId(), delivery.getChannel());
    }

    private void handleFailure(Delivery delivery, String error, ErrorType errorType) {
        Instant now = clock.instant();
        delivery.setStatus(DeliveryStatus.FAILED);
        delivery.setLastError(error);
        delivery.setLastErrorType(errorType);

        if (errorType == ErrorType.PERMANENT) {
            delivery.setNextRetryAt(null);
            if (delivery.getChannel() == NotificationChannel.MOBILE_PUSH && delivery.getDeviceId() != null) {
                deviceRepository.findById(delivery.getDeviceId())
                    .ifPresent(device -> {
                        device.markInvalid();
                        deviceRepository.save(device);
                        logger.info("Marked device {} as invalid due to permanent error", device.getDeviceId());
                    });
            }
        } else {
            if (delivery.canRetry()) {
                delivery.scheduleNextRetry();
                DeliveryEvent retryEvent = DeliveryEvent.retryScheduled(
                    delivery.getDeliveryId(),
                    "Next retry at: " + delivery.getNextRetryAt(),
                    now
                );
                eventRepository.save(retryEvent);
            } else {
                delivery.setStatus(DeliveryStatus.EXHAUSTED);
                delivery.setNextRetryAt(null);
                DeliveryEvent exhaustedEvent = DeliveryEvent.exhausted(
                    delivery.getDeliveryId(),
                    "Max attempts reached",
                    now
                );
                eventRepository.save(exhaustedEvent);
            }
        }

        deliveryRepository.save(delivery);

        DeliveryEvent failedEvent = DeliveryEvent.failed(delivery.getDeliveryId(), error, now);
        eventRepository.save(failedEvent);

        logger.error("Failed to dispatch delivery {}: {}", delivery.getDeliveryId(), error);
    }

    public NotificationService getService(NotificationChannel channel) {
        return serviceMap.get(channel);
    }
}