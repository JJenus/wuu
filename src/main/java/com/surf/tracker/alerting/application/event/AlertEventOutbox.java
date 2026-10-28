package com.surf.tracker.alerting.application.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.surf.tracker.alerting.domain.AlertDetectedEvent;
import com.surf.tracker.shared.events.AlertRaisedEvent;
import com.surf.tracker.shared.pubsub.EventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** Durable, at-least-once publication; source identities are retained after sending. */
@Service
public class AlertEventOutbox {
    private static final Logger logger = LoggerFactory.getLogger(AlertEventOutbox.class);
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final EventPublisher publisher;
    private final Clock clock;

    public AlertEventOutbox(JdbcTemplate jdbc, ObjectMapper mapper, EventPublisher publisher, Clock clock) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.publisher = publisher;
        this.clock = clock;
    }

    public boolean isReplay(AlertDetectedEvent source, Long organizationId) {
        var owners = jdbc.query("select organization_id, vehicle_id from alert_event_outbox where source_event_id = ?",
            (row, index) -> new Owner(row.getLong("organization_id"), row.getString("vehicle_id")), source.getEventId());
        if (owners.isEmpty()) return false;
        Owner owner = owners.getFirst();
        if (!organizationId.equals(owner.organizationId()) || !source.getVehicleId().equals(owner.vehicleId())) {
            throw new IllegalArgumentException("Source event identity belongs to another vehicle or organization");
        }
        return true;
    }

    public void enqueue(AlertDetectedEvent source, Long organizationId, AlertRaisedEvent event) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Alert and publication intent require one transaction");
        }
        try {
            jdbc.update("insert into alert_event_outbox (source_event_id, organization_id, vehicle_id, alert_id, "
                    + "payload, created_at, next_attempt_at) values (?, ?, ?, ?, ?, ?, ?)",
                source.getEventId(), organizationId, source.getVehicleId(), Long.valueOf(event.getAlertId()),
                mapper.writeValueAsString(event), sqlTime(clock.instant()), sqlTime(clock.instant()));
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Could not serialize alert publication intent", failure);
        }
    }

    @Scheduled(fixedDelayString = "${tracking.alert-outbox.poll-delay-ms:5000}")
    @Transactional
    public void relayPending() {
        var pending = jdbc.query("select source_event_id, payload, attempts from alert_event_outbox "
                + "where published_at is null and next_attempt_at <= ? order by created_at "
                + "limit 20 for update skip locked",
            (row, index) -> new Pending(row.getString("source_event_id"), row.getString("payload"), row.getInt("attempts")),
            sqlTime(clock.instant()));
        for (Pending record : pending) {
            try {
                publisher.publish(mapper.readValue(record.payload(), AlertRaisedEvent.class));
                jdbc.update("update alert_event_outbox set published_at = ? where source_event_id = ?",
                    sqlTime(clock.instant()), record.sourceId());
            } catch (Exception failure) {
                long backoffSeconds = 1L << Math.min(record.attempts() + 1, 8);
                jdbc.update("update alert_event_outbox set attempts = attempts + 1, next_attempt_at = ? "
                        + "where source_event_id = ?", sqlTime(clock.instant().plusSeconds(backoffSeconds)), record.sourceId());
                logger.warn("Alert publication deferred for source event {}", record.sourceId(), failure);
            }
        }
    }

    private static OffsetDateTime sqlTime(Instant instant) { return instant.atOffset(ZoneOffset.UTC); }
    private record Owner(Long organizationId, String vehicleId) {}
    private record Pending(String sourceId, String payload, int attempts) {}
}
