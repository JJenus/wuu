package com.surf.tracker.notification.application.event;

import com.surf.tracker.notification.application.NotificationOrchestrator;
import com.surf.tracker.shared.events.AlertRaisedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
public class NotificationEventHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(NotificationEventHandler.class);
    
    private final NotificationOrchestrator orchestrator;
    
    public NotificationEventHandler(NotificationOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }
    
    @JmsListener(
        destination = "tracking.events.alertraisedevent",
        containerFactory = "topicJmsListenerContainerFactory"
    )
    public void handleAlertRaised(@Payload AlertRaisedEvent event) {
        try {
            logger.info("Received alert for notification processing: {}", event.getAlertId());
            orchestrator.processAlert(event);
        } catch (Exception e) {
            logger.error("Failed to process alert notification for event: {}", 
                       event.getEventId(), e);
            // Don't rethrow to avoid blocking the queue
        }
    }
}
