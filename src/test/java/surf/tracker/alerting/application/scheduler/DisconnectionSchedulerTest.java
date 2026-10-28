package com.surf.tracker.alerting.application.scheduler;

import com.surf.tracker.alerting.application.dedup.AlertDeduplicator;
import com.surf.tracker.alerting.domain.AlertDetectedEvent;
import com.surf.tracker.alerting.domain.enums.AlertSeverity;
import com.surf.tracker.alerting.domain.enums.AlertType;
import com.surf.tracker.shared.redis.VehicleActivityTracker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DisconnectionSchedulerTest {

    @Mock
    private VehicleActivityTracker activityTracker;

    @Mock
    private AlertDeduplicator deduplicator;

    @Mock
    private com.surf.tracker.shared.pubsub.EventPublisher eventPublisher;

    private DisconnectionScheduler scheduler;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-30T14:00:00Z"), ZoneOffset.UTC);
        scheduler = new DisconnectionScheduler(activityTracker, deduplicator, eventPublisher, clock);
        ReflectionTestUtils.setField(scheduler, "thresholdMinutes", 5);
    }

    private void withActiveVehicles(List<String> ids, Runnable assertions) {
        doAnswer(inv -> {
            Consumer<List<String>> consumer = inv.getArgument(0);
            consumer.accept(ids);
            return null;
        }).when(activityTracker).getAllActiveVehicleIdsBatched(any());
        assertions.run();
    }

    @Test
    void checkDisconnectedVehicles_silentVehicle_publishesAlert() {
        Instant tenMinutesAgo = Instant.parse("2026-08-30T14:00:00Z").minusSeconds(600);

        withActiveVehicles(List.of("v1"), () -> {
            when(activityTracker.getLastSeen("v1")).thenReturn(Optional.of(tenMinutesAgo));
            when(deduplicator.tryAcquire(any(AlertDetectedEvent.class))).thenReturn(true);

            scheduler.checkDisconnectedVehicles();
        });

        ArgumentCaptor<AlertDetectedEvent> captor = ArgumentCaptor.forClass(AlertDetectedEvent.class);
        verify(eventPublisher).publish(captor.capture());
        assertThat(captor.getValue().getVehicleId()).isEqualTo("v1");
        assertThat(captor.getValue().getAlertType()).isEqualTo(AlertType.DEVICE_DISCONNECTED);
    }

    @Test
    void checkDisconnectedVehicles_vehicleWithinThreshold_doesNotPublish() {
        Instant oneMinuteAgo = Instant.parse("2026-08-30T14:00:00Z").minusSeconds(60);

        withActiveVehicles(List.of("v1"), () -> {
            when(activityTracker.getLastSeen("v1")).thenReturn(Optional.of(oneMinuteAgo));

            scheduler.checkDisconnectedVehicles();
        });

        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void checkDisconnectedVehicles_vehicleNotSeen_doesNotCrash() {
        withActiveVehicles(List.of("v1"), () -> {
            when(activityTracker.getLastSeen("v1")).thenReturn(Optional.empty());

            scheduler.checkDisconnectedVehicles();
        });

        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void checkDisconnectedVehicles_multipleVehicles_publishesForEach() {
        Instant tenMinutesAgo = Instant.parse("2026-08-30T14:00:00Z").minusSeconds(600);

        withActiveVehicles(List.of("v1", "v2"), () -> {
            when(activityTracker.getLastSeen("v1")).thenReturn(Optional.of(tenMinutesAgo));
            when(activityTracker.getLastSeen("v2")).thenReturn(Optional.of(tenMinutesAgo));
            when(deduplicator.tryAcquire(any())).thenReturn(true);

            scheduler.checkDisconnectedVehicles();
        });

        verify(eventPublisher, times(2)).publish(any(AlertDetectedEvent.class));
    }

    @Test
    void checkDisconnectedVehicles_allActive_doesNotPublish() {
        Instant justNow = Instant.parse("2026-08-30T14:00:00Z");

        withActiveVehicles(List.of("v1"), () -> {
            when(activityTracker.getLastSeen("v1")).thenReturn(Optional.of(justNow));

            scheduler.checkDisconnectedVehicles();
        });

        verify(eventPublisher, never()).publish(any());
    }
}
