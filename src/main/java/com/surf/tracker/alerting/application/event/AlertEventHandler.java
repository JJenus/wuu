package com.surf.tracker.alerting.application.event;

import com.surf.tracker.alerting.application.AlertingEngine;
import com.surf.tracker.alerting.application.DeviceAlarmProcessor;
import com.surf.tracker.shared.events.VehicleUpdatedEvent;
import com.surf.tracker.shared.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
public class AlertEventHandler {
    private static final Logger logger = LoggerFactory.getLogger(AlertEventHandler.class);

    private final AlertingEngine alertingEngine;
    private final DeviceAlarmProcessor deviceAlarms;
    private final java.time.Clock clock;
    @org.springframework.beans.factory.annotation.Value("${tracking.telemetry.max-alert-age-seconds:300}")
    private int maxAlertAgeSeconds = 300;
    @org.springframework.beans.factory.annotation.Value("${tracking.telemetry.max-future-skew-seconds:300}")
    private int maxFutureSkewSeconds = 300;

    public AlertEventHandler(AlertingEngine alertingEngine, DeviceAlarmProcessor deviceAlarms, java.time.Clock clock) {
        this.alertingEngine = alertingEngine;
        this.deviceAlarms = deviceAlarms;
        this.clock = clock;
    }

    @org.springframework.jms.annotation.JmsListeners({
    @JmsListener(destination = "tracking.work.vehicleupdatedevent", containerFactory = "queueJmsListenerContainerFactory", concurrency = "1"),
    @JmsListener(
            destination = "tracking.events.vehicleupdatedevent",
            containerFactory = "topicJmsListenerContainerFactory"
    )})
    public void handleVehicleUpdate(@Payload VehicleUpdatedEvent event) {
        if (event.getOrganizationId() == null || event.getOrganizationId() <= 0) {
            throw new IllegalArgumentException("Vehicle update requires organization identity");
        }
        if (event.getNewLocation() == null || !event.getNewLocation().isValid())
            throw new IllegalArgumentException("Vehicle update requires a valid GPS fix");
        var observed = event.getNewLocation().timestamp();
        if (observed.isBefore(clock.instant().minusSeconds(maxAlertAgeSeconds))
                || observed.isAfter(clock.instant().plusSeconds(maxFutureSkewSeconds))) {
            logger.info("Skipping out-of-window queued update for vehicle {}", event.getVehicleId());
            return; // Location history is already committed; do not recreate stale live effects.
        }
        Long previousOrg = TenantContext.getCurrentOrgId();
        Long previousUser = TenantContext.getCurrentUserId();
        try {
            TenantContext.clear();
            TenantContext.setCurrentOrgId(event.getOrganizationId());
            logger.info("Alert Processing received LOCATION update for device {}", event.getVehicleId());
            logger.debug("Processing vehicle update for {}", event.getEventId());

            // Device-reported alarms first: they need no configured rule, which the engine requires.
            deviceAlarms.process(event);
            alertingEngine.processVehicleUpdate(event);
        } catch (Exception e) {
            logger.error("Failed to process vehicle update for {}",
                    event.getEventId(), e);
            throw e;
        } finally {
            TenantContext.clear();
            if (previousOrg != null) TenantContext.setCurrentOrgId(previousOrg);
            if (previousUser != null) TenantContext.setCurrentUserId(previousUser);
        }
    }

}
