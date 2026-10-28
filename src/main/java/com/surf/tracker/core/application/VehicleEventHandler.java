package com.surf.tracker.core.application;

import com.surf.tracker.core.application.service.VehicleService;
import com.surf.tracker.core.domain.entity.Vehicle;
import com.surf.tracker.core.infrastructure.repository.TrackerRepository;
import com.surf.tracker.shared.security.TenantContext;
import com.surf.tracker.shared.events.LocationDataEvent;
import com.surf.tracker.shared.events.VehicleUpdatedEvent;
import com.surf.tracker.shared.metrics.MetricsRegistry;
import com.surf.tracker.shared.pubsub.EventPublisher;
import com.surf.tracker.shared.redis.VehicleActivityTracker;
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
    private final TrackerRepository trackerRepository;

    public VehicleEventHandler(
            VehicleService vehicleService,
            EventPublisher eventPublisher,
            VehicleActivityTracker activityTracker,
            MetricsRegistry metrics,
            Clock clock, TrackerRepository trackerRepository) {
        this.vehicleService = vehicleService;
        this.eventPublisher = eventPublisher;
        this.activityTracker = activityTracker;
        this.metrics = metrics;
        this.clock = clock;
        this.trackerRepository = trackerRepository;
    }

    @JmsListener(destination = "tracking.events.locationdataevent",
            containerFactory = "topicJmsListenerContainerFactory",
            concurrency = "1"
    )
    @Transactional
    public void handleLocationUpdate(@Payload LocationDataEvent event) {
        Long previousOrg = TenantContext.getCurrentOrgId();
        Long previousUser = TenantContext.getCurrentUserId();
        try {
            var tracker = trackerRepository.findForTelemetryByDeviceId(event.getDeviceId());
            Vehicle vehicle = tracker.map(com.surf.tracker.core.domain.entity.Tracker::getVehicle).orElse(null);
            if (vehicle == null || vehicle.getOrganizationId() == null || vehicle.getOrganizationId() <= 0) {
                logger.warn("Skipping location update for device {}: no tenant-owned vehicle assignment", event.getDeviceId());
                metrics.increment("telemetry.packets.skipped");
                return;
            }
            TenantContext.clear();
            TenantContext.setCurrentOrgId(vehicle.getOrganizationId());
            String vehicleId = vehicle.getVehicleId();

            vehicleService.updateVehicleLocation(vehicleId, event.getDeviceId(), event.getLocation());

            Instant now = clock.instant();
            activityTracker.recordActivity(vehicleId, now);
            metrics.increment("telemetry.packets.received", "protocol", event.getProtocol());

            VehicleUpdatedEvent vehicleUpdatedEvent = new VehicleUpdatedEvent(
                    derivedUpdateId(event, vehicle),
                    event.getOccurredOn(),
                    vehicleId,
                    vehicle.getOrganizationId(),
                    event.getLocation(),
                    event.getMetaData()
            );
            eventPublisher.publish(vehicleUpdatedEvent);

            metrics.increment("telemetry.vehicles.updated");
            logger.debug("Updated vehicle {} for device {}", vehicleId, event.getDeviceId());

        } catch (Exception e) {
            logger.error("Failed to process location update for device {}",
                    event != null ? event.getDeviceId() : "unknown", e);
            metrics.increment("telemetry.packets.failed");
            throw e;
        } finally {
            TenantContext.clear();
            if (previousOrg != null) TenantContext.setCurrentOrgId(previousOrg);
            if (previousUser != null) TenantContext.setCurrentUserId(previousUser);
        }
    }

    private String derivedUpdateId(LocationDataEvent source, Vehicle vehicle) {
        StringBuilder identity = new StringBuilder("location-vehicle-update:v1:");
        for (String component : new String[]{source.getEventId(), vehicle.getOrganizationId().toString(),
                source.getDeviceId(), vehicle.getVehicleId()}) {
            identity.append(component.length()).append(':').append(component);
        }
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(identity.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("Required SHA-256 algorithm unavailable", impossible);
        }
    }
}
