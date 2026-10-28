package com.surf.tracker.shared.pubsub;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

@Component
public class ArtemisEventPublisher implements EventPublisher {
    private static final Logger logger = LoggerFactory.getLogger(ArtemisEventPublisher.class);

    private final JmsTemplate jmsTemplate;
    private final ObjectMapper objectMapper;

    public ArtemisEventPublisher(@Qualifier("topicJmsTemplate") JmsTemplate jmsTemplate, ObjectMapper objectMapper) {
        this.jmsTemplate = jmsTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publish(DomainEvent event) {
        try {
            String destination = "tracking.events." + event.getClass().getSimpleName().toLowerCase();

            logger.info("🟢 PUBLISHING to destination: {}", destination);
            logger.info("🟢 Event class: {}", event.getClass().getName());

            // TRY to serialize - log any errors
            String json = null;
            try {
                json = objectMapper.writeValueAsString(event);
                logger.info("🟢 JSON: {}", json);
            } catch (Exception e) {
                logger.error("🔴 FAILED to serialize event to JSON", e);
                logger.error("🔴 Event details: {}", event);
                throw e; // Re-throw
            }

            // Publish
            jmsTemplate.convertAndSend(destination, event);
            logger.info("🟢✅ Published event {} to {}", event.getEventId(), destination);

        } catch (Exception e) {
            logger.error("🔴 FAILED to publish event {}", event != null ? event.getEventId() : "null", e);
            throw new RuntimeException("Failed to publish event", e);
        }
    }
}