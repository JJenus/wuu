package com.surf.tracker.alerting.application.event;

import com.surf.tracker.alerting.api.dto.AlertResponse;
import com.surf.tracker.core.domain.entity.Vehicle;
import com.surf.tracker.core.infrastructure.repository.VehicleRepository;
import com.surf.tracker.shared.security.TenantContext;
import com.surf.tracker.alerting.application.service.AlertService;
import com.surf.tracker.alerting.domain.AlertDetectedEvent;
import com.surf.tracker.alerting.domain.enums.AlertSeverity;
import com.surf.tracker.alerting.domain.enums.AlertType;
import com.surf.tracker.shared.events.AlertRaisedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AlertCreationEventHandlerTest {
    private final AlertService alerts = mock(AlertService.class);
    private final AlertEventOutbox outbox = mock(AlertEventOutbox.class);
    private final VehicleRepository vehicles = mock(VehicleRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"), ZoneOffset.UTC);
    private final AlertCreationEventHandler handler = new AlertCreationEventHandler(
        alerts, vehicles, outbox, clock);

    @BeforeEach
    void setUp() {
        Vehicle vehicle = new Vehicle();
        vehicle.setVehicleId("vehicle");
        vehicle.setOrganizationId(11L);
        when(vehicles.findForAlertProcessing("vehicle")).thenReturn(Optional.of(vehicle));
    }

    @AfterEach
    void clearContext() { TenantContext.clear(); }

    @Test
    void handleAlertEvent_savedAlert_publishesPersistedIdentityNotRuleKey() {
        // given
        TenantContext.setCurrentOrgId(99L);
        TenantContext.setCurrentUserId(88L);
        AlertResponse saved = new AlertResponse();
        saved.setAlertId(42L);
        doAnswer(invocation -> {
            assertThat(TenantContext.getCurrentOrgId()).isEqualTo(11L);
            assertThat(TenantContext.getCurrentUserId()).isNull();
            return saved;
        }).when(alerts).processAutomatedAlert(anyString(), anyString(), any(), any(), anyString(), anyMap());
        // when
        handler.handleAlertEvent(event());
        // then
        verify(outbox).enqueue(any(), eq(11L), argThat(raised ->
            "42".equals(raised.getAlertId()) && "speed-rule".equals(raised.getRuleKey())));
        assertThat(TenantContext.getCurrentOrgId()).isEqualTo(99L);
        assertThat(TenantContext.getCurrentUserId()).isEqualTo(88L);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0, -1})
    void handleAlertEvent_invalidOwner_rejectsWithoutPersistenceAndPreservesContext(Long organizationId) {
        // given
        Vehicle vehicle = new Vehicle();
        vehicle.setVehicleId("vehicle");
        vehicle.setOrganizationId(organizationId);
        when(vehicles.findForAlertProcessing("vehicle")).thenReturn(Optional.of(vehicle));
        TenantContext.setCurrentOrgId(99L);
        TenantContext.setCurrentUserId(88L);
        // when / then
        assertThatThrownBy(() -> handler.handleAlertEvent(event()))
            .isInstanceOf(IllegalArgumentException.class).hasMessage("Alert vehicle has no organization");
        verifyNoInteractions(alerts, outbox);
        assertThat(TenantContext.getCurrentOrgId()).isEqualTo(99L);
        assertThat(TenantContext.getCurrentUserId()).isEqualTo(88L);
    }

    @Test
    void handleAlertEvent_missingVehicle_rejectsWithoutPersistenceAndPreservesContext() {
        // given
        when(vehicles.findForAlertProcessing("vehicle")).thenReturn(Optional.empty());
        TenantContext.setCurrentOrgId(99L);
        TenantContext.setCurrentUserId(88L);
        // when / then
        assertThatThrownBy(() -> handler.handleAlertEvent(event()))
            .isInstanceOf(IllegalArgumentException.class).hasMessage("Alert vehicle not found");
        verifyNoInteractions(alerts, outbox);
        assertThat(TenantContext.getCurrentOrgId()).isEqualTo(99L);
        assertThat(TenantContext.getCurrentUserId()).isEqualTo(88L);
    }

    @Test
    void handleAlertEvent_persistenceFailure_propagatesForBrokerRecovery() {
        // given
        TenantContext.setCurrentOrgId(99L);
        TenantContext.setCurrentUserId(88L);
        doThrow(new RuntimeException("database unavailable")).when(alerts)
            .processAutomatedAlert(anyString(), anyString(), any(), any(), anyString(), anyMap());
        // when / then
        assertThatThrownBy(() -> handler.handleAlertEvent(event())).hasMessage("database unavailable");
        verify(outbox, never()).enqueue(any(), any(), any());
        assertThat(TenantContext.getCurrentOrgId()).isEqualTo(99L);
        assertThat(TenantContext.getCurrentUserId()).isEqualTo(88L);
    }

    @Test
    void listeners_vehicleUpdate_hasExactlyOneEvaluationConsumer() {
        // when
        long consumers = Stream.of(AlertEventHandler.class, AlertCreationEventHandler.class)
            .flatMap(type -> Arrays.stream(type.getDeclaredMethods()))
            .map(method -> method.getAnnotation(org.springframework.jms.annotation.JmsListener.class))
            .filter(annotation -> annotation != null && annotation.destination().equals("tracking.events.vehicleupdatedevent"))
            .count();
        // then
        assertThat(consumers).isEqualTo(1);
    }

    private AlertDetectedEvent event() {
        return new AlertDetectedEvent(clock, "speed-rule", AlertType.OVERSPEED, "vehicle", "Speed exceeded", AlertSeverity.WARNING, null);
    }
}
