package com.surf.tracker.devicecomm.publish;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.surf.tracker.shared.domain.DeviceAlarm;
import com.surf.tracker.shared.domain.DeviceHealth;
import com.surf.tracker.shared.domain.LocationPoint;
import com.surf.tracker.shared.events.LocationDataEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jms.core.JmsTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DurableLocationPublisherTest {
    @TempDir Path directory;
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule())
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-12T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void publish_brokerUnavailable_retainsAcrossRestartAndReplaysOriginalEnvelope() throws Exception {
        // given
        var queue = mock(JmsTemplate.class);
        var source = event("00000000-0000-0000-0000-000000000001");
        doThrow(new IllegalStateException("broker unavailable")).when(queue).convertAndSend(anyString(), any(LocationDataEvent.class));
        // when: accepting a parsed fix does not need the broker to be up.
        try (var spool = spool(queue, 10)) {
            spool.publish(source);
            assertThat(spool.pendingCount()).isEqualTo(1);
            spool.relayPending();
            assertThat(spool.pendingCount()).isEqualTo(1);
        }
        reset(queue);
        try (var recovered = spool(queue, 10)) {
            assertThat(recovered.pendingCount()).isEqualTo(1);
            recovered.relayPending();
            // then: crash/restart and replay cannot mint a new source identity/time.
            var captured = org.mockito.ArgumentCaptor.forClass(LocationDataEvent.class);
            verify(queue).convertAndSend(eq("tracking.telemetry.locations"), captured.capture());
            assertThat(captured.getValue()).usingRecursiveComparison().isEqualTo(source);
            assertThat(recovered.pendingCount()).isZero();
        }
    }

    @Test
    void publish_eventWithHealthAndAlarms_replaysHealthExactlyAcrossRestart() throws Exception {
        // given: partially reported health, including alarms and unreported (null) fields
        var queue = mock(JmsTemplate.class);
        var health = new DeviceHealth(null, null, 25, 10, java.util.Set.of(DeviceAlarm.SOS, DeviceAlarm.OIL_CUT));
        var source = event("00000000-0000-0000-0000-000000000003", health);
        doThrow(new IllegalStateException("broker unavailable")).when(queue).convertAndSend(anyString(), any(LocationDataEvent.class));
        try (var spool = spool(queue, 10)) {
            spool.publish(source);
            spool.relayPending();
        }
        reset(queue);
        // when
        try (var recovered = spool(queue, 10)) {
            recovered.relayPending();
            // then: health survives the durable envelope without nulls turning into zeros
            var captured = org.mockito.ArgumentCaptor.forClass(LocationDataEvent.class);
            verify(queue).convertAndSend(eq("tracking.telemetry.locations"), captured.capture());
            assertThat(captured.getValue().getHealth()).isEqualTo(health);
            assertThat(captured.getValue().getHealth().batteryPercent()).isNull();
        }
    }

    @Test
    void publish_capacityReached_rejectsNewFixWithoutEvictingRetainedFix() throws Exception {
        var queue = mock(JmsTemplate.class);
        try (var spool = spool(queue, 1)) {
            spool.publish(event("00000000-0000-0000-0000-000000000001"));
            assertThatThrownBy(() -> spool.publish(event("00000000-0000-0000-0000-000000000002")))
                    .isInstanceOf(com.surf.tracker.shared.exception.InfrastructureException.class);
            assertThat(spool.pendingCount()).isEqualTo(1);
            spool.relayPending();
            var captured = org.mockito.ArgumentCaptor.forClass(LocationDataEvent.class);
            verify(queue).convertAndSend(anyString(), captured.capture());
            assertThat(captured.getValue().getEventId()).endsWith("0001");
        }
    }

    @Test
    void start_corruptRecord_failsClosedAndPreservesEvidence() throws Exception {
        var corrupt = directory.resolve("00000000000000000001-00000000-0000-0000-0000-000000000001.json");
        Files.writeString(corrupt, "truncated record");
        assertThatThrownBy(() -> spool(mock(JmsTemplate.class), 10)).isInstanceOf(IllegalStateException.class);
        assertThat(Files.readString(corrupt)).isEqualTo("truncated record");
    }

    @Test
    void start_uncommittedTemporaryFile_stopsForRecoveryWithoutDeletingIt() throws Exception {
        var incomplete = directory.resolve("interrupted.tmp");
        Files.writeString(incomplete, "partial capture");
        assertThatThrownBy(() -> spool(mock(JmsTemplate.class), 10)).isInstanceOf(IllegalStateException.class);
        assertThat(incomplete).exists();
    }

    @Test
    void relay_corruptionAfterStartup_retainsRecordAndFailsFurtherCaptureClosed() throws Exception {
        var queue = mock(JmsTemplate.class);
        try (var spool = spool(queue, 10)) {
            spool.publish(event("00000000-0000-0000-0000-000000000001"));
            Path retained;
            try (var files = Files.list(directory)) {
                retained = files.filter(file -> file.toString().endsWith(".json")).findFirst().orElseThrow();
            }
            Files.writeString(retained, "{}");
            spool.relayPending();
            verifyNoInteractions(queue);
            assertThat(retained).exists();
            assertThatThrownBy(() -> spool.publish(event("00000000-0000-0000-0000-000000000002")))
                    .isInstanceOf(com.surf.tracker.shared.exception.InfrastructureException.class);
        }
    }

    @Test
    void publish_byteCapacityReached_doesNotEvictEarlierRecords() throws Exception {
        try (var spool = new DurableLocationPublisher(directory, mapper, mock(JmsTemplate.class), clock, 100, 1024)) {
            int accepted = 0;
            for (int index = 0; index < 100; index++) {
                var next = event(new UUID(0, index + 1).toString());
                try { spool.publish(next); accepted++; }
                catch (com.surf.tracker.shared.exception.InfrastructureException full) { break; }
            }
            assertThat(accepted).isBetween(1, 99);
            assertThat(spool.pendingCount()).isEqualTo(accepted);
        }
    }

    @Test
    void publish_slowRelay_doesNotBlockDurableCaptureOfLaterPacket() throws Exception {
        var queue = mock(JmsTemplate.class);
        var entered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        doAnswer(invocation -> {
            entered.countDown();
            if (!release.await(5, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("Test relay timed out");
            return null;
        }).when(queue).convertAndSend(anyString(), any(LocationDataEvent.class));
        try (var spool = spool(queue, 10); var workers = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            spool.publish(event("00000000-0000-0000-0000-000000000001"));
            var relay = workers.submit(spool::relayPending);
            try {
                assertThat(entered.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                workers.submit(() -> spool.publish(event("00000000-0000-0000-0000-000000000002")))
                        .get(2, java.util.concurrent.TimeUnit.SECONDS);
                assertThat(spool.pendingCount()).isEqualTo(2);
            } finally { release.countDown(); }
            relay.get(5, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(spool.pendingCount()).isZero();
        }
    }

    @Test
    void start_existingOwner_rejectsSecondProcessWithoutStealingItsSpool() throws Exception {
        try (var first = spool(mock(JmsTemplate.class), 10)) {
            assertThatThrownBy(() -> spool(mock(JmsTemplate.class), 10)).isInstanceOf(IllegalStateException.class);
            assertThat(first.pendingCount()).isZero();
        }
        try (var next = spool(mock(JmsTemplate.class), 10)) {
            assertThat(next.pendingCount()).isZero();
        }
    }

    @Test
    void relay_multipleFixes_preservesCaptureOrder() throws Exception {
        var queue = mock(JmsTemplate.class);
        try (var spool = spool(queue, 10)) {
            var first = event("00000000-0000-0000-0000-000000000002");
            var second = event("00000000-0000-0000-0000-000000000001");
            spool.publish(first);
            spool.publish(second);
        }
        try (var recovered = spool(queue, 10)) {
            recovered.relayPending();
            var order = inOrder(queue);
            order.verify(queue).convertAndSend(anyString(), argThat(
                    (LocationDataEvent source) -> source.getEventId().endsWith("0002")));
            order.verify(queue).convertAndSend(anyString(), argThat(
                    (LocationDataEvent source) -> source.getEventId().endsWith("0001")));
        }
    }

    private DurableLocationPublisher spool(JmsTemplate queue, int maxFiles) {
        return new DurableLocationPublisher(directory, mapper, queue, clock, maxFiles, 1_048_576);
    }

    private LocationDataEvent event(String id) {
        return event(id, DeviceHealth.NONE);
    }

    private LocationDataEvent event(String id, DeviceHealth health) {
        return new LocationDataEvent(clock, UUID.fromString(id), "8168000008",
                new LocationPoint(51.5, -0.1, 24, clock.instant()), "AUTOSEEKER", null, health);
    }
}
