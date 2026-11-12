package com.surf.tracker.devicecomm.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
        var corrupt = directory.resolve("00000000000000000001-broken.json");
        Files.writeString(corrupt, "truncated record");
        assertThatThrownBy(() -> spool(mock(JmsTemplate.class), 10)).isInstanceOf(IllegalStateException.class);
        assertThat(Files.readString(corrupt)).isEqualTo("truncated record");
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
        return new LocationDataEvent(clock, UUID.fromString(id), "8168000008",
                new LocationPoint(51.5, -0.1, 24, clock.instant()), "AUTOSEEKER", null);
    }
}
