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
    private final JmsTemplate queueTemplate;

    public ArtemisEventPublisher(@Qualifier("topicJmsTemplate") JmsTemplate jmsTemplate,
                                 @Qualifier("queueJmsTemplate") JmsTemplate queueTemplate, ObjectMapper objectMapper) {
        this.jmsTemplate = jmsTemplate;
        this.objectMapper = objectMapper;
        this.queueTemplate = queueTemplate;
    }

    @Override
    public void publish(DomainEvent event) {
        try {
            boolean durableWork = event instanceof DurableWorkEvent;
            String destination = (durableWork ? "tracking.work." : "tracking.events.")
                    + event.getClass().getSimpleName().toLowerCase(java.util.Locale.ROOT);

            logger.info("🟢 PUBLISHING to destination: {}", destination);
            logger.info("🟢 Event class: {}", event.getClass().getName());

            // TRY to serialize - log any errors
            String json = null;
            try {
                json = objectMapper.writeValueAsString(event);
                logger.debug("Event envelope serialized successfully");
            } catch (Exception e) {
                logger.error("🔴 FAILED to serialize event to JSON", e);
                logger.error("🔴 Event details: {}", event);
                throw e; // Re-throw
            }

            // Publish
            (durableWork ? queueTemplate : jmsTemplate).convertAndSend(destination, event);
            logger.info("🟢✅ Published event {} to {}", event.getEventId(), destination);

        } catch (Exception e) {
            logger.error("🔴 FAILED to publish event {}", event != null ? event.getEventId() : "null", e);
            throw new RuntimeException("Failed to publish event", e);
        }
    }
}
