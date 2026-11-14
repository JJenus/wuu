package com.surf.tracker.alerting.application;

import com.surf.tracker.alerting.application.dedup.AlertDeduplicator;
import com.surf.tracker.alerting.domain.AlertDetectedEvent;
import com.surf.tracker.alerting.domain.enums.AlertSeverity;
import com.surf.tracker.alerting.domain.enums.AlertType;
import com.surf.tracker.shared.domain.DeviceAlarm;
import com.surf.tracker.shared.events.VehicleUpdatedEvent;
import com.surf.tracker.shared.metrics.MetricsRegistry;
import com.surf.tracker.shared.pubsub.EventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.Map;

/**
 * Turns alarms the device itself reported (SOS, tamper, power cut, low battery) into alerts.
 * Unlike {@link AlertingEngine} these need no user-configured rule: a panic button must raise an
 * alert for every vehicle. Alarms the server computes from positions (geofence, speed, idle) stay
 * with the engine, so device-side duplicates of those are counted but not raised.
 */
@Component
public class DeviceAlarmProcessor {
    private static final Logger logger = LoggerFactory.getLogger(DeviceAlarmProcessor.class);
    private static final int ALARM_COOLDOWN_MINUTES = 5;
    private static final int LOW_BATTERY_COOLDOWN_MINUTES = 60;

    private record Mapping(AlertType type, AlertSeverity severity, String message) { }

    private static final Map<DeviceAlarm, Mapping> MAPPINGS = new EnumMap<>(DeviceAlarm.class);
    static {
        MAPPINGS.put(DeviceAlarm.SOS, new Mapping(AlertType.PANIC_BUTTON_PRESSED, AlertSeverity.CRITICAL,
                "SOS / panic button pressed on the tracker"));
        MAPPINGS.put(DeviceAlarm.TAMPER, new Mapping(AlertType.TAMPER_DETECTED, AlertSeverity.CRITICAL,
                "Tracker reported tampering"));
        MAPPINGS.put(DeviceAlarm.BATTERY_REMOVED, new Mapping(AlertType.TAMPER_DETECTED, AlertSeverity.CRITICAL,
                "Tracker battery was disconnected"));
        MAPPINGS.put(DeviceAlarm.POWER_CUT, new Mapping(AlertType.TAMPER_DETECTED, AlertSeverity.CRITICAL,
                "Tracker external power was cut"));
        MAPPINGS.put(DeviceAlarm.LOW_BATTERY, new Mapping(AlertType.LOW_BATTERY, AlertSeverity.WARNING,
                "Tracker reported a low battery"));
    }

    private final EventPublisher eventPublisher;
    private final AlertDeduplicator deduplicator;
    private final MetricsRegistry metrics;
    @Value("${tracking.device.low-battery-percent:15}")
    private int lowBatteryPercent = 15;

    public DeviceAlarmProcessor(EventPublisher eventPublisher, AlertDeduplicator deduplicator, MetricsRegistry metrics) {
        this.eventPublisher = eventPublisher;
        this.deduplicator = deduplicator;
        this.metrics = metrics;
    }

    /** Publishes one deduplicated alert per mapped device alarm. Transport failures propagate for redelivery. */
    public void process(VehicleUpdatedEvent source) {
        var health = source.getHealth();
        var alarms = new java.util.TreeSet<DeviceAlarm>(health.alarms());
        boolean batteryLow = health.batteryPercent() != null && health.batteryPercent() <= lowBatteryPercent;
        if (batteryLow) alarms.add(DeviceAlarm.LOW_BATTERY);
        for (DeviceAlarm alarm : alarms) {
            Mapping mapping = MAPPINGS.get(alarm);
            if (mapping == null) {
                logger.debug("Device alarm {} on vehicle {} has no alert mapping", alarm, source.getVehicleId());
                metrics.increment("device.alarm.unmapped", "alarm", alarm.name());
                continue;
            }
            int cooldown = alarm == DeviceAlarm.LOW_BATTERY ? LOW_BATTERY_COOLDOWN_MINUTES : ALARM_COOLDOWN_MINUTES;
            String ruleKey = "device-alarm:" + alarm.name().toLowerCase(java.util.Locale.ROOT);
            var alert = new AlertDetectedEvent(identity(source, ruleKey), source.getOccurredOn(), ruleKey,
                    mapping.type(), source.getVehicleId(), mapping.message(), mapping.severity(),
                    source.getOccurredOn(), source.getNewLocation(), cooldown);
            var lease = deduplicator.acquire(alert);
            if (lease == null) continue;
            try {
                eventPublisher.publish(alert);
            } catch (RuntimeException failure) {
                deduplicator.release(lease);
                throw failure;
            }
            logger.info("Device alarm {} raised for vehicle {}", alarm, source.getVehicleId());
            metrics.increment("alert.triggered", "type", mapping.type().name());
        }
    }

    /** Same source update + alarm always yields the same id, so a redelivered update cannot raise twice. */
    private static String identity(VehicleUpdatedEvent source, String ruleKey) {
        StringBuilder identity = new StringBuilder("device-alarm:v1:");
        for (String part : new String[]{source.getOrganizationId().toString(), source.getEventId(),
                source.getVehicleId(), ruleKey}) {
            identity.append(part.length()).append(':').append(part);
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(identity.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("Required SHA-256 algorithm unavailable", impossible);
        }
    }
}
