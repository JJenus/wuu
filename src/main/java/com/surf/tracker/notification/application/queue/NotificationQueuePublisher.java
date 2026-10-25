package com.surf.tracker.notification.application.queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

@Component
public class NotificationQueuePublisher {

    private static final Logger logger = LoggerFactory.getLogger(NotificationQueuePublisher.class);

    public static final String PROCESS_QUEUE = "notification.process";
    public static final String DLQ_QUEUE = "notification.dlq";

    private final JmsTemplate queueJmsTemplate;

    public NotificationQueuePublisher(@Qualifier("queueJmsTemplate") JmsTemplate queueJmsTemplate) {
        this.queueJmsTemplate = queueJmsTemplate;
    }

    public void publish(NotificationMessage message) {
        try {
            logger.info("Publishing notification message to queue {} for user {}",
                PROCESS_QUEUE, message.getUserId());
            queueJmsTemplate.convertAndSend(PROCESS_QUEUE, message);
        } catch (Exception e) {
            logger.error("Failed to publish notification message", e);
            throw new RuntimeException("Failed to publish notification message", e);
        }
    }

    public void sendToDlq(NotificationMessage message) {
        try {
            logger.warn("Sending message to DLQ {} for user {}", DLQ_QUEUE, message.getUserId());
            queueJmsTemplate.convertAndSend(DLQ_QUEUE, message);
        } catch (Exception e) {
            logger.error("Failed to send message to DLQ", e);
        }
    }
}