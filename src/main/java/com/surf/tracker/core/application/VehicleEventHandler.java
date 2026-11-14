package com.surf.tracker.core.application;

import com.surf.tracker.core.application.service.VehicleService;
import com.surf.tracker.core.domain.entity.Tracker;
import com.surf.tracker.core.domain.entity.Vehicle;
import com.surf.tracker.shared.domain.DeviceHealth;
import com.surf.tracker.core.infrastructure.repository.TrackerRepository;
import com.surf.tracker.shared.security.TenantContext;
import com.surf.tracker.shared.events.LocationDataEvent;
import com.surf.tracker.shared.events.VehicleUpdatedEvent;
import com.surf.tracker.shared.metrics.MetricsRegistry;
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
    private final VehicleActivityTracker activityTracker;
    private final MetricsRegistry metrics;
    private final Clock clock;
    private final TrackerRepository trackerRepository;
    private final TelemetryReceiptStore receipts;
    @org.springframework.beans.factory.annotation.Value("${tracking.telemetry.max-alert-age-seconds:300}")
    private int maxAlertAgeSeconds = 300;
    @org.springframework.beans.factory.annotation.Value("${tracking.telemetry.max-future-skew-seconds:300}")
    private int maxFutureSkewSeconds = 300;

    @jakarta.annotation.PostConstruct
    void validateTimePolicy() {
        if (maxAlertAgeSeconds < 1 || maxFutureSkewSeconds < 0)
            throw new IllegalArgumentException("Invalid telemetry clock policy");
    }

    public VehicleEventHandler(
            VehicleService vehicleService,
            VehicleActivityTracker activityTracker,
            MetricsRegistry metrics,
            Clock clock, TrackerRepository trackerRepository, TelemetryReceiptStore receipts) {
        this.vehicleService = vehicleService;
        this.activityTracker = activityTracker;
        this.metrics = metrics;
        this.clock = clock;
        this.trackerRepository = trackerRepository;
        this.receipts = receipts;
    }

    @org.springframework.jms.annotation.JmsListeners({
    @JmsListener(destination = "tracking.telemetry.locations", containerFactory = "queueJmsListenerContainerFactory", concurrency = "1"),
    @JmsListener(destination = "tracking.events.locationdataevent",
            containerFactory = "topicJmsListenerContainerFactory",
            concurrency = "1"
    )})
    @Transactional
    public void handleLocationUpdate(@Payload LocationDataEvent event) {
        Long previousOrg = TenantContext.getCurrentOrgId();
        Long previousUser = TenantContext.getCurrentUserId();
        try {
            var tracker = trackerRepository.findForTelemetryByDeviceId(event.getDeviceId());
            Vehicle vehicle = tracker.map(Tracker::getVehicle).orElse(null);
            if (vehicle == null || vehicle.getOrganizationId() == null || vehicle.getOrganizationId() <= 0) {
                logger.warn("Skipping location update for device {}: no tenant-owned vehicle assignment", event.getDeviceId());
                metrics.increment("telemetry.packets.skipped");
                return;
            }
            TenantContext.clear();
            TenantContext.setCurrentOrgId(vehicle.getOrganizationId());
            String vehicleId = vehicle.getVehicleId();
            String receiptKey = TelemetryReceiptStore.keyFor(event, vehicle.getOrganizationId(), vehicleId);
            if (receipts.isReplay(receiptKey)) {
                metrics.increment("telemetry.packets.replayed");
                return;
            }
            boolean latest = vehicle.getCurrentLocation() == null
                    || vehicle.getCurrentLocation().getRecordedAt() == null
                    || event.getLocation().timestamp().isAfter(vehicle.getCurrentLocation().getRecordedAt());

            vehicleService.updateVehicleLocation(vehicleId, event.getDeviceId(), event.getLocation());
            if (latest) applyHealth(tracker.get(), event.getHealth());
            boolean timely = !event.getLocation().timestamp().isBefore(clock.instant().minusSeconds(maxAlertAgeSeconds))
                    && !event.getLocation().timestamp().isAfter(clock.instant().plusSeconds(maxFutureSkewSeconds));
            if (!latest || !timely) {
                receipts.record(receiptKey, vehicle.getOrganizationId(), vehicleId, event, null);
                metrics.increment("telemetry.packets.historical");
                return;
            }

            Instant now = clock.instant();
            activityTracker.recordActivity(vehicleId, now);
            metrics.increment("telemetry.packets.received", "protocol", event.getProtocol());

            VehicleUpdatedEvent vehicleUpdatedEvent = new VehicleUpdatedEvent(
                    derivedUpdateId(event, vehicle),
                    event.getOccurredOn(),
                    vehicleId,
                    vehicle.getOrganizationId(),
                    event.getLocation(),
                    event.getMetaData(),
                    event.getHealth()
            );
            receipts.record(receiptKey, vehicle.getOrganizationId(), vehicleId, event, vehicleUpdatedEvent);

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

    /** Only reported values overwrite; a frame without battery or signal leaves the last known reading. */
    private static void applyHealth(Tracker tracker, DeviceHealth health) {
        if (health.batteryPercent() != null) tracker.updateBatteryLevel(health.batteryPercent().floatValue());
        if (health.gsmSignal() != null) tracker.setSignalStrength(health.gsmSignal());
    }

    private String derivedUpdateId(LocationDataEvent source, Vehicle vehicle) {
        return TelemetryReceiptStore.keyFor(source, vehicle.getOrganizationId(), vehicle.getVehicleId());
    }
}
