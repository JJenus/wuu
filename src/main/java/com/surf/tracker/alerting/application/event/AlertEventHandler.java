package com.surf.tracker.alerting.application.event;

import com.surf.tracker.alerting.application.AlertingEngine;
import com.surf.tracker.shared.events.VehicleUpdatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
public class AlertEventHandler {
    private static final Logger logger = LoggerFactory.getLogger(AlertEventHandler.class);

    private final AlertingEngine alertingEngine;

    public AlertEventHandler(AlertingEngine alertingEngine) {
        this.alertingEngine = alertingEngine;
    }

    @JmsListener(
            destination = "tracking.events.vehicleupdatedevent",
            containerFactory = "topicJmsListenerContainerFactory"
    )
    public void handleVehicleUpdate(@Payload VehicleUpdatedEvent event) {
        try {
            logger.info("Alert Processing received LOCATION update for device {}", event.getVehicleId());
            logger.debug("Processing vehicle update for {}", event.getEventId());

             alertingEngine.processVehicleUpdate(event.getVehicleId(), event.getNewLocation());
        } catch (Exception e) {
            logger.error("Failed to process vehicle update for {}",
                    event.getEventId(), e);
            throw e;
        }
    }

}