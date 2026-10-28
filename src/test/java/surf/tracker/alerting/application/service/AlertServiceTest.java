package com.surf.tracker.alerting.application.service;

import com.surf.tracker.alerting.api.dto.AlertResponse;
import com.surf.tracker.alerting.api.dto.UpdateAlertRequest;
import com.surf.tracker.alerting.domain.entity.TrackerAlert;
import com.surf.tracker.alerting.domain.enums.AlertSeverity;
import com.surf.tracker.alerting.domain.enums.AlertType;
import com.surf.tracker.alerting.exception.AlertException;
import com.surf.tracker.alerting.infrastructure.repository.TrackerAlertRepository;
import com.surf.tracker.shared.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
        Instant.parse("2026-08-30T12:00:00Z"), ZoneOffset.UTC);

    @Mock
    private TrackerAlertRepository alertRepository;

    @Mock
    private AlertService.AlertQueryService alertQueryService;

    private AlertService alertService;
    private TrackerAlert alert;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentOrgId(1L);
        alertService = new AlertService(alertRepository, alertQueryService, FIXED_CLOCK);

        alert = new TrackerAlert();
        alert.setAlertId(1L);
        alert.setOrganizationId(1L);
        alert.setVehicle("vehicle-001");
        alert.setTracker("tracker-001");
        alert.setAlertType(AlertType.OVERSPEED);
        alert.setSeverity(AlertSeverity.WARNING);
        alert.setMessage("original message");
        alert.setTriggeredAt(FIXED_CLOCK.instant());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void updateAlert_validRequest_updatesMessageAndSeverity() {
        // given
        UpdateAlertRequest request = new UpdateAlertRequest();
        request.setMessage("new message");
        request.setSeverity(AlertSeverity.CRITICAL);
        when(alertRepository.findByIdAndOrganizationId(1L, 1L)).thenReturn(Optional.of(alert));
        when(alertRepository.save(any(TrackerAlert.class))).thenReturn(alert);

        // when
        AlertResponse result = alertService.updateAlert(1L, request);

        // then
        assertThat(result).isNotNull();
        assertThat(alert.getMessage()).isEqualTo("new message");
        assertThat(alert.getSeverity()).isEqualTo(AlertSeverity.CRITICAL);
        verify(alertRepository).save(alert);
    }

    @Test
    void updateAlert_noChanges_returnsUnchanged() {
        // given
        UpdateAlertRequest request = new UpdateAlertRequest();
        when(alertRepository.findByIdAndOrganizationId(1L, 1L)).thenReturn(Optional.of(alert));

        // when
        AlertResponse result = alertService.updateAlert(1L, request);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getMessage()).isEqualTo("original message");
        verify(alertRepository, never()).save(any());
    }

    @Test
    void updateAlert_alertNotInOrg_throws() {
        // given
        UpdateAlertRequest request = new UpdateAlertRequest();
        request.setMessage("new message");
        when(alertRepository.findByIdAndOrganizationId(1L, 1L)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> alertService.updateAlert(1L, request))
            .isInstanceOf(AlertException.class)
            .hasMessageContaining("not found");
    }

    @Test
    void deleteAlert_existingAlert_deletes() {
        // given
        when(alertRepository.findByIdAndOrganizationId(1L, 1L)).thenReturn(Optional.of(alert));

        // when
        alertService.deleteAlert(1L);

        // then
        verify(alertRepository).delete(alert);
    }

    @Test
    void deleteAlert_alertNotInOrg_throws() {
        // given
        when(alertRepository.findByIdAndOrganizationId(1L, 1L)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> alertService.deleteAlert(1L))
            .isInstanceOf(AlertException.class)
            .hasMessageContaining("not found");
    }
}