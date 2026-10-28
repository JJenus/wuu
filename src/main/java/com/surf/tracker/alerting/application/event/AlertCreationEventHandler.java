package com.surf.tracker.alerting.application.event;

import com.surf.tracker.alerting.api.dto.AlertResponse;
import com.surf.tracker.core.infrastructure.repository.VehicleRepository;
import com.surf.tracker.shared.security.TenantContext;
import com.surf.tracker.alerting.application.service.AlertService;
import com.surf.tracker.alerting.domain.AlertDetectedEvent;
import com.surf.tracker.shared.events.AlertRaisedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;

@Component
public class AlertCreationEventHandler {

    private static final Logger logger = LoggerFactory.getLogger(AlertCreationEventHandler.class);

    private final AlertService alertService;
    private final VehicleRepository vehicles;
    private final AlertEventOutbox outbox;
    private final Clock clock;

    public AlertCreationEventHandler(
            AlertService alertService,
            VehicleRepository vehicles,
            AlertEventOutbox outbox,
            Clock clock) {
        this.alertService = alertService;
        this.vehicles = vehicles;
        this.outbox = outbox;
        this.clock = clock;
    }

    @JmsListener(
            destination = "tracking.events.alertdetectedevent",
            containerFactory = "topicJmsListenerContainerFactory",
            concurrency = "1"
    )
    @org.springframework.transaction.annotation.Transactional
    public void handleAlertEvent(@Payload AlertDetectedEvent event) {
        Long previousOrg = TenantContext.getCurrentOrgId();
        Long previousUser = TenantContext.getCurrentUserId();
        try {
            if (event.getEventId().isBlank() || event.getEventId().length() > 64) {
                throw new IllegalArgumentException("Invalid source event identity");
            }
            var vehicle = vehicles.findForAlertProcessing(event.getVehicleId())
                    .orElseThrow(() -> new IllegalArgumentException("Alert vehicle not found"));
            if (vehicle.getOrganizationId() == null || vehicle.getOrganizationId() <= 0) {
                throw new IllegalArgumentException("Alert vehicle has no organization");
            }
            TenantContext.clear();
            TenantContext.setCurrentOrgId(vehicle.getOrganizationId());
            if (outbox.isReplay(event, vehicle.getOrganizationId())) return;
            logger.info("Processing alert event: {} for vehicle {}",
                       event.getRuleKey(), event.getVehicleId());

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("ruleKey", event.getRuleKey());
            metadata.put("alertTimestamp", event.getAlertTimestamp());
            metadata.put("organizationId", vehicle.getOrganizationId());

            if (event.getLocation() != null) {
                metadata.put("latitude", event.getLocation().latitude());
                metadata.put("longitude", event.getLocation().longitude());
                metadata.put("speedKmh", event.getLocation().speedKmh());
            }

            AlertResponse created = alertService.processAutomatedAlert(
                event.getVehicleId(),
                "system",
                event.getAlertType(),
                event.getSeverity(),
                event.getMessage(),
                metadata
            );

            AlertRaisedEvent alertRaisedEvent = new AlertRaisedEvent(
                    clock,
                    created.getAlertId().toString(),
                    event.getRuleKey(),
                    event.getVehicleId(),
                    event.getAlertType().name(),
                    event.getSeverity().name(),
                    event.getMessage(),
                    event.getAlertTimestamp(),
                    event.getLocation() != null ? event.getLocation().latitude() : null,
                    event.getLocation() != null ? event.getLocation().longitude() : null,
                    event.getLocation() != null ? (double) event.getLocation().speedKmh() : null,
                    metadata
            );

            outbox.enqueue(event, vehicle.getOrganizationId(), alertRaisedEvent);
            logger.debug("Alert publication intent persisted: {}", event.getRuleKey());
        } catch (Exception e) {
            logger.error("Failed to process alert event: {}", event.getRuleKey(), e);
            throw e;
        } finally {
            TenantContext.clear();
            if (previousOrg != null) TenantContext.setCurrentOrgId(previousOrg);
            if (previousUser != null) TenantContext.setCurrentUserId(previousUser);
        }
    }
}
