package com.surf.tracker.alerting.application.scheduler;

import com.surf.tracker.alerting.application.dedup.AlertDeduplicator;
import com.surf.tracker.alerting.domain.AlertDetectedEvent;
import com.surf.tracker.alerting.domain.enums.AlertSeverity;
import com.surf.tracker.alerting.domain.enums.AlertType;
import com.surf.tracker.shared.domain.LocationPoint;
import com.surf.tracker.shared.pubsub.EventPublisher;
import com.surf.tracker.shared.redis.VehicleActivityTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
public class DisconnectionScheduler {

    private static final Logger logger = LoggerFactory.getLogger(DisconnectionScheduler.class);

    private final VehicleActivityTracker activityTracker;
    private final AlertDeduplicator deduplicator;
    private final EventPublisher eventPublisher;
    private final Clock clock;

    @Value("${alerting.disconnect.threshold-minutes:5}")
    private int thresholdMinutes;

    public DisconnectionScheduler(
            VehicleActivityTracker activityTracker,
            AlertDeduplicator deduplicator,
            EventPublisher eventPublisher,
            Clock clock) {
        this.activityTracker = activityTracker;
        this.deduplicator = deduplicator;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${alerting.disconnect.check-interval-ms:60000}")
    public void checkDisconnectedVehicles() {
        Instant now = clock.instant();
        int totalDisconnected = 0;
        int[] disconnectedHolder = {0};

        activityTracker.getAllActiveVehicleIdsBatched(batch -> {
            for (String vehicleId : batch) {
                Optional<Instant> lastSeen = activityTracker.getLastSeen(vehicleId);
                if (lastSeen.isEmpty()) {
                    continue;
                }
                Duration silence = Duration.between(lastSeen.get(), now);
                if (silence.toMinutes() >= thresholdMinutes) {
                    disconnectedHolder[0]++;
                    raiseDisconnectedAlert(vehicleId, lastSeen.get(), silence);
                }
            }
        });
        totalDisconnected = disconnectedHolder[0];

        if (totalDisconnected > 0) {
            logger.info("Detected {} disconnected vehicles (threshold={}min)",
                    totalDisconnected, thresholdMinutes);
        }
    }

    private void raiseDisconnectedAlert(String vehicleId, Instant lastSeen, Duration silence) {
        AlertDetectedEvent event = new AlertDetectedEvent(
                "device-disconnect",
                AlertType.DEVICE_DISCONNECTED,
                vehicleId,
                String.format("Vehicle %s has been silent for %d minutes (last seen: %s)",
                        vehicleId, silence.toMinutes(), lastSeen),
                AlertSeverity.WARNING,
                null
        );

        if (deduplicator.tryAcquire(event)) {
            logger.warn("Vehicle {} is disconnected (silent for {} minutes)",
                    vehicleId, silence.toMinutes());
            eventPublisher.publish(event);
        }
    }
}
