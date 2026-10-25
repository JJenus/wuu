package com.surf.tracker.core.application;

import com.surf.tracker.core.application.service.VehicleService;
import com.surf.tracker.core.domain.FuelCutRequestedEvent;
import com.surf.tracker.shared.events.LocationDataEvent;
import com.surf.tracker.shared.events.VehicleUpdatedEvent;
import com.surf.tracker.shared.metrics.MetricsRegistry;
import com.surf.tracker.shared.pubsub.EventPublisher;
import com.surf.tracker.shared.redis.VehicleActivityTracker;
import com.surf.tracker.shared.util.TimeProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Component
public class VehicleEventHandler {
    private static final Logger logger = LoggerFactory.getLogger(VehicleEventHandler.class);

    private final VehicleService vehicleService;
    private final EventPublisher eventPublisher;
    private final VehicleActivityTracker activityTracker;
    private final MetricsRegistry metrics;
    private final Clock clock;

    public VehicleEventHandler(
            VehicleService vehicleService,
            EventPublisher eventPublisher,
            VehicleActivityTracker activityTracker,
            MetricsRegistry metrics,
            Clock clock) {
        this.vehicleService = vehicleService;
        this.eventPublisher = eventPublisher;
        this.activityTracker = activityTracker;
        this.metrics = metrics;
        this.clock = clock;
    }

    @JmsListener(destination = "tracking.events.locationdataevent",
            containerFactory = "topicJmsListenerContainerFactory",
            concurrency = "1"
    )
    @Transactional
    public void handleLocationUpdate(@Payload LocationDataEvent event) {
        try {
            String vehicleId = vehicleService.findVehicleIdForDevice(event.getDeviceId());

            vehicleService.updateVehicleLocation(vehicleId, event.getLocation());

            Instant now = clock.instant();
            activityTracker.recordActivity(vehicleId, now);
            metrics.increment("telemetry.packets.received", "protocol", event.getProtocol());

            VehicleUpdatedEvent vehicleUpdatedEvent = new VehicleUpdatedEvent(
                    clock,
                    TimeProvider.newUuid(),
                    vehicleId,
                    event.getLocation(),
                    event.getMetaData()
            );
            eventPublisher.publish(vehicleUpdatedEvent);

            metrics.increment("telemetry.vehicles.updated");
            logger.debug("Updated vehicle {} for device {}", vehicleId, event.getDeviceId());

        } catch (IllegalArgumentException | IllegalStateException e) {
            logger.warn("Skipping location update for device {}: {}",
                    event != null ? event.getDeviceId() : "unknown", e.getMessage());
            metrics.increment("telemetry.packets.skipped");
        } catch (Exception e) {
            logger.error("Failed to process location update for device {}",
                    event != null ? event.getDeviceId() : "unknown", e);
            metrics.increment("telemetry.packets.failed");
            throw e;
        }
    }

    @JmsListener(destination = "tracking.events.fuelcutrequestedevent",
            containerFactory = "topicJmsListenerContainerFactory",
            concurrency = "1"
    )
    public void handleFuelCutRequest(@Payload FuelCutRequestedEvent event) {
        try {
            logger.info("Received fuel cut request for vehicle {}", event.getVehicleId());
            eventPublisher.publish(event);
        } catch (Exception e) {
            logger.error("Failed to forward fuel cut request for vehicle {}",
                    event != null ? event.getVehicleId() : "unknown", e);
        }
    }
}
