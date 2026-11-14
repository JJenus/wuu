package com.surf.tracker.alerting.application;

import com.surf.tracker.alerting.application.dedup.AlertDeduplicator;
import com.surf.tracker.alerting.domain.AlertDetectedEvent;
import com.surf.tracker.alerting.domain.enums.AlertSeverity;
import com.surf.tracker.alerting.domain.enums.AlertType;
import com.surf.tracker.shared.domain.DeviceAlarm;
import com.surf.tracker.shared.domain.DeviceHealth;
import com.surf.tracker.shared.domain.LocationPoint;
import com.surf.tracker.shared.events.VehicleUpdatedEvent;
import com.surf.tracker.shared.metrics.MetricsRegistry;
import com.surf.tracker.shared.pubsub.EventPublisher;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DeviceAlarmProcessorTest {
    private final EventPublisher publisher = mock(EventPublisher.class);
    private final AlertDeduplicator deduplicator = mock(AlertDeduplicator.class);
    private final MetricsRegistry metrics = mock(MetricsRegistry.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    private final DeviceAlarmProcessor processor = new DeviceAlarmProcessor(publisher, deduplicator, metrics);
    private final AlertDeduplicator.Lease lease = new AlertDeduplicator.Lease("key", "token");

    @Test
    void process_sosAlarm_raisesCriticalPanicAlertWithoutAnyConfiguredRule() {
        // given
        when(deduplicator.acquire(any())).thenReturn(lease);
        // when
        processor.process(update(DeviceHealth.ofAlarms(Set.of(DeviceAlarm.SOS))));
        // then
        var alert = published();
        assertThat(alert.getAlertType()).isEqualTo(AlertType.PANIC_BUTTON_PRESSED);
        assertThat(alert.getSeverity()).isEqualTo(AlertSeverity.CRITICAL);
        assertThat(alert.getVehicleId()).isEqualTo("vehicle-1");
        assertThat(alert.getRuleKey()).isEqualTo("device-alarm:sos");
        assertThat(alert.getLocation()).isNotNull();
    }

    @Test
    void process_sameUpdateTwice_derivesSameAlertIdentity() {
        // given
        when(deduplicator.acquire(any())).thenReturn(lease);
        var source = update(DeviceHealth.ofAlarms(Set.of(DeviceAlarm.TAMPER)));
        // when
        processor.process(source);
        processor.process(source);
        // then: a redelivered update cannot mint a second alert identity
        var captured = ArgumentCaptor.forClass(AlertDetectedEvent.class);
        verify(publisher, times(2)).publish(captured.capture());
        assertThat(captured.getAllValues().get(0).getEventId()).isEqualTo(captured.getAllValues().get(1).getEventId());
    }

    @Test
    void process_cooldownActive_publishesNothing() {
        // given
        when(deduplicator.acquire(any())).thenReturn(null);
        // when
        processor.process(update(DeviceHealth.ofAlarms(Set.of(DeviceAlarm.SOS))));
        // then
        verifyNoInteractions(publisher);
    }

    @Test
    void process_lowBatteryPercent_raisesLowBatteryWarning() {
        // given
        when(deduplicator.acquire(any())).thenReturn(lease);
        // when
        processor.process(update(DeviceHealth.ofBattery(10, null)));
        // then
        var alert = published();
        assertThat(alert.getAlertType()).isEqualTo(AlertType.LOW_BATTERY);
        assertThat(alert.getSeverity()).isEqualTo(AlertSeverity.WARNING);
        assertThat(alert.getCooldownMinutes()).isEqualTo(60);
    }

    @Test
    void process_healthyBatteryAndNoAlarms_doesNothing() {
        processor.process(update(DeviceHealth.ofBattery(90, null)));
        processor.process(update(DeviceHealth.NONE));
        verifyNoInteractions(publisher, deduplicator);
    }

    @Test
    void process_alarmWithoutMapping_isCountedNotRaised() {
        // when
        processor.process(update(DeviceHealth.ofAlarms(Set.of(DeviceAlarm.VIBRATION))));
        // then
        verifyNoInteractions(publisher);
        verify(metrics).increment("device.alarm.unmapped", "alarm", "VIBRATION");
    }

    @Test
    void process_publishFails_releasesLeaseAndPropagatesForRedelivery() {
        // given
        when(deduplicator.acquire(any())).thenReturn(lease);
        doThrow(new IllegalStateException("broker down")).when(publisher).publish(any());
        // when / then
        assertThatThrownBy(() -> processor.process(update(DeviceHealth.ofAlarms(Set.of(DeviceAlarm.SOS)))))
                .hasMessage("broker down");
        verify(deduplicator).release(lease);
    }

    private AlertDetectedEvent published() {
        var captured = ArgumentCaptor.forClass(AlertDetectedEvent.class);
        verify(publisher).publish(captured.capture());
        return captured.getValue();
    }

    private VehicleUpdatedEvent update(DeviceHealth health) {
        return new VehicleUpdatedEvent(clock, UUID.fromString("00000000-0000-0000-0000-000000000005"), "vehicle-1", 11L,
                new LocationPoint(1, 2, 0, clock.instant()), null, health);
    }
}
