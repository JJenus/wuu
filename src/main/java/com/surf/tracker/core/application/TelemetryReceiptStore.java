package com.surf.tracker.core.application;

import com.surf.tracker.shared.events.LocationDataEvent;
import com.surf.tracker.shared.events.VehicleUpdatedEvent;
import com.surf.tracker.shared.pubsub.EventPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.HexFormat;

/** Transactional fix-level deduplication, including retransmissions with new source UUIDs. */
@Component
public class TelemetryReceiptStore {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final ObjectMapper mapper;
    private final EventPublisher publisher;

    public TelemetryReceiptStore(JdbcTemplate jdbc, Clock clock, ObjectMapper mapper, EventPublisher publisher) {
        this.jdbc = jdbc; this.clock = clock; this.mapper = mapper; this.publisher = publisher;
    }

    public boolean isReplay(String key) {
        return jdbc.queryForObject("select count(*) from telemetry_receipts where receipt_key=?", Long.class, key) > 0;
    }

    public void record(String key, Long organization, String vehicle, LocationDataEvent event, VehicleUpdatedEvent update) {
        if (!TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Telemetry receipt and location require one transaction");
        try {
            jdbc.update("insert into telemetry_receipts (receipt_key, organization_id, vehicle_id, device_id, recorded_at, received_at, "
                            + "update_payload, next_attempt_at) values (?, ?, ?, ?, ?, ?, ?, ?)", key, organization, vehicle, event.getDeviceId(),
                    event.getLocation().timestamp().atOffset(ZoneOffset.UTC), clock.instant().atOffset(ZoneOffset.UTC),
                    update == null ? null : mapper.writeValueAsString(update), clock.instant().atOffset(ZoneOffset.UTC));
        } catch (JsonProcessingException failure) { throw new IllegalStateException("Could not retain vehicle update intent", failure); }
    }

    @Scheduled(fixedDelayString = "${tracking.telemetry-outbox.poll-delay-ms:1000}",
            initialDelayString = "${tracking.telemetry-outbox.initial-delay-ms:1000}")
    @Transactional
    public void relayPending() {
        var pending = jdbc.query("select receipt_key, update_payload, attempts, next_attempt_at from telemetry_receipts "
                        + "where update_payload is not null and published_at is null "
                        + "order by capture_sequence limit 100 for update",
                (row, index) -> new PendingUpdate(row.getString(1), row.getString(2), row.getInt(3),
                        row.getObject(4, java.time.OffsetDateTime.class).toInstant()));
        for (PendingUpdate update : pending) {
            if (update.nextAttempt().isAfter(clock.instant())) break;
            try {
                publisher.publish(mapper.readValue(update.payload(), VehicleUpdatedEvent.class));
                jdbc.update("update telemetry_receipts set published_at=? where receipt_key=?",
                        clock.instant().atOffset(ZoneOffset.UTC), update.key());
            } catch (Exception failure) {
                jdbc.update("update telemetry_receipts set attempts=attempts+1, next_attempt_at=? where receipt_key=?",
                        clock.instant().plusSeconds(1L << Math.min(update.attempts() + 1, 8)).atOffset(ZoneOffset.UTC), update.key());
                org.slf4j.LoggerFactory.getLogger(TelemetryReceiptStore.class)
                        .warn("Vehicle update publication deferred for retained fix {}", update.key(), failure);
                break; // Do not overtake an earlier failed publication in this batch.
            }
        }
    }

    private record PendingUpdate(String key, String payload, int attempts, java.time.Instant nextAttempt) { }

    public static String keyFor(LocationDataEvent event, Long organization, String vehicle) {
        var fix = event.getLocation();
        if (fix == null || !fix.isValid() || !Float.isFinite(fix.speedKmh()))
            throw new IllegalArgumentException("Invalid telemetry fix");
        var identity = new StringBuilder("telemetry-fix:v1:");
        for (String part : new String[]{organization.toString(), vehicle, event.getDeviceId(), event.getProtocol(),
                fix.timestamp().toString(), Long.toHexString(Double.doubleToLongBits(fix.latitude())),
                Long.toHexString(Double.doubleToLongBits(fix.longitude())), Integer.toHexString(Float.floatToIntBits(fix.speedKmh()))}) {
            if (part == null) throw new IllegalArgumentException("Incomplete telemetry identity");
            identity.append(part.length()).append(':').append(part);
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(identity.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
