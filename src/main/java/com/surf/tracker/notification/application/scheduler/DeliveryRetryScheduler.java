package com.surf.tracker.notification.application.scheduler;

import com.surf.tracker.notification.application.NotificationDispatcher;
import com.surf.tracker.notification.domain.entity.Delivery;
import com.surf.tracker.notification.domain.entity.DeliveryEvent;
import com.surf.tracker.notification.domain.enums.DeliveryEventType;
import com.surf.tracker.notification.domain.enums.DeliveryStatus;
import com.surf.tracker.notification.infrastructure.repository.DeliveryEventRepository;
import com.surf.tracker.notification.infrastructure.repository.DeliveryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Component
public class DeliveryRetryScheduler {

    private static final Logger logger = LoggerFactory.getLogger(DeliveryRetryScheduler.class);

    private final DeliveryRepository deliveryRepository;
    private final DeliveryEventRepository eventRepository;
    private final NotificationDispatcher dispatcher;
    private final Clock clock;

    public DeliveryRetryScheduler(DeliveryRepository deliveryRepository,
                                 DeliveryEventRepository eventRepository,
                                 NotificationDispatcher dispatcher,
                                 Clock clock) {
        this.deliveryRepository = deliveryRepository;
        this.eventRepository = eventRepository;
        this.dispatcher = dispatcher;
        this.clock = clock;
    }

    @Scheduled(fixedDelay = 30000)
    @Transactional
    public void processRetries() {
        logger.info("Starting retry scheduler run");
        Instant now = clock.instant();

        List<Delivery> failedDeliveries = deliveryRepository
            .findByStatusAndNextRetryAtLessThanEqual(DeliveryStatus.FAILED, now);

        logger.info("Found {} deliveries ready for retry", failedDeliveries.size());

        int successCount = 0;
        int failureCount = 0;

        for (Delivery delivery : failedDeliveries) {
            if (!delivery.canRetry()) {
                logger.debug("Delivery {} cannot be retried (status={}, attempts={})",
                    delivery.getDeliveryId(), delivery.getStatus(), delivery.getAttemptCount());
                continue;
            }

            try {
                logger.debug("Retrying delivery {}", delivery.getDeliveryId());
                dispatcher.redispatch(delivery);
                successCount++;
            } catch (Exception e) {
                logger.error("Retry failed for delivery {}", delivery.getDeliveryId(), e);
                failureCount++;
            }
        }

        logger.info("Retry scheduler completed: success={}, failures={}", successCount, failureCount);
    }
}
