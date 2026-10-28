package com.surf.tracker.notification.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.surf.tracker.notification.application.queue.NotificationMessage;
import com.surf.tracker.notification.application.queue.NotificationQueuePublisher;
import com.surf.tracker.notification.domain.enums.NotificationChannel;
import com.surf.tracker.notification.infrastructure.repository.*;
import com.surf.tracker.shared.events.AlertRaisedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationOrchestratorTest {
    @Mock NotificationHubRepository hubs;
    @Mock NotificationTemplateRepository templates;
    @Mock DeliveryRepository deliveries;
    @Mock DeliveryEventRepository events;
    @Mock NotificationQueuePublisher queue;
    @Mock PreferenceResolver preferences;
    NotificationOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new NotificationOrchestrator(hubs, templates, deliveries, events, queue,
            preferences, new ObjectMapper(), Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
            () -> UUID.fromString("00000000-0000-0000-0000-000000000001"));
    }

    @Test
    void processAlert_missingRecipientPolicy_rejectsWithoutFabricatingAdmin() {
        // given
        var alert = new AlertRaisedEvent("source", Instant.EPOCH, "alert", "rule", "vehicle",
            "OVERSPEED", "WARNING", "fixture", Instant.EPOCH, null, null, null, Map.of());
        // when / then
        assertThatThrownBy(() -> orchestrator.processAlert(alert))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("recipient policy");
        verifyNoInteractions(queue, hubs, templates, deliveries, events, preferences);
    }

    @ParameterizedTest
    @EnumSource(value = NotificationChannel.class, names = {"EMAIL", "SMS", "MOBILE_PUSH", "SLACK", "TEAMS"})
    void processNotification_unresolvedChannelRecipient_rejectsBeforePersistence(NotificationChannel channel) {
        // given
        var message = new NotificationMessage("member-17", "alert", "OVERSPEED", Map.of());
        message.setTenantId("tenant-1");
        when(templates.findByTemplateId("OVERSPEED")).thenReturn(Optional.empty());
        when(preferences.resolvePreferences(eq("member-17"), anyString(), any()))
            .thenReturn(Map.of(channel, new PreferenceResolver.ChannelPreference(true, false)));
        // when / then
        assertThatThrownBy(() -> orchestrator.processNotification(message))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("recipient").hasMessageContaining(channel.name());
        verifyNoInteractions(hubs, deliveries, events, queue);
    }
}
