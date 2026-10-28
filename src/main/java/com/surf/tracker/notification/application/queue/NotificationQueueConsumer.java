package com.surf.tracker.notification.application.queue;

import com.surf.tracker.notification.application.NotificationOrchestrator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
public class NotificationQueueConsumer {

    private static final Logger logger = LoggerFactory.getLogger(NotificationQueueConsumer.class);

    private final NotificationOrchestrator orchestrator;
    private final NotificationQueuePublisher publisher;

    public NotificationQueueConsumer(NotificationOrchestrator orchestrator,
                                     NotificationQueuePublisher publisher) {
        this.orchestrator = orchestrator;
        this.publisher = publisher;
    }

    @JmsListener(
        destination = NotificationQueuePublisher.PROCESS_QUEUE,
        containerFactory = "queueJmsListenerContainerFactory"
    )
    public void onMessage(@Payload NotificationMessage message) {
        try {
            logger.info("Received notification message for user {}", message.getUserId());
            orchestrator.processNotification(message);
        } catch (Exception e) {
            logger.error("Failed to process notification message for user {}",
                message.getUserId(), e);
            publisher.sendToDlq(message);
        }
    }
}