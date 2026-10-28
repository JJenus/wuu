package com.surf.tracker.alerting.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.surf.tracker.alerting.api.dto.AlertResponse;
import com.surf.tracker.alerting.api.dto.AlertSearchRequest;
import com.surf.tracker.alerting.api.dto.CreateAlertRequest;
import com.surf.tracker.alerting.api.dto.PagedResponse;
import com.surf.tracker.alerting.api.dto.UpdateAlertRequest;
import com.surf.tracker.alerting.application.service.AlertService;
import com.surf.tracker.alerting.domain.enums.AlertSeverity;
import com.surf.tracker.alerting.domain.enums.AlertType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AlertControllerTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
        Instant.parse("2026-08-30T12:00:00Z"), ZoneOffset.UTC);

    @Mock
    private AlertService alertService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        mockMvc = MockMvcBuilders.standaloneSetup(new AlertController(alertService, FIXED_CLOCK)).build();
    }

    @Test
    void createAlert_validRequest_returnsCreated() throws Exception {
        // given
        CreateAlertRequest request = new CreateAlertRequest();
        request.setVehicleId("vehicle-001");
        request.setTrackerId("tracker-001");
        request.setAlertType(AlertType.OVERSPEED);
        request.setSeverity(AlertSeverity.WARNING);
        request.setMessage("overspeed detected");
        when(alertService.createAlert(any(CreateAlertRequest.class))).thenReturn(alertResponse());

        String body = objectMapper.writeValueAsString(request);

        // when & then
        mockMvc.perform(post("/alerts")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.alertId").value(1L));
    }

    @Test
    void getAlert_existingAlert_returnsAlert() throws Exception {
        // given
        when(alertService.getAlertById(1L)).thenReturn(alertResponse());

        // when & then
        mockMvc.perform(get("/alerts/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.alertId").value(1L))
            .andExpect(jsonPath("$.vehicleId").value("vehicle-001"));
    }

    @Test
    void searchAlerts_validParams_returnsPagedAlerts() throws Exception {
        // given
        AlertResponse response = alertResponse();
        PagedResponse<AlertResponse> paged = new PagedResponse<>(
            new PageImpl<>(List.of(response), PageRequest.of(0, 20), 1));
        when(alertService.searchAlerts(any(AlertSearchRequest.class))).thenReturn(paged);

        // when & then
        mockMvc.perform(get("/alerts"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].alertId").value(1L))
            .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void updateAlert_validRequest_returnsUpdatedAlert() throws Exception {
        // given
        UpdateAlertRequest request = new UpdateAlertRequest();
        request.setMessage("updated message");
        request.setSeverity(AlertSeverity.CRITICAL);
        when(alertService.updateAlert(any(Long.class), any(UpdateAlertRequest.class)))
            .thenReturn(alertResponse());

        String body = objectMapper.writeValueAsString(request);

        // when & then
        mockMvc.perform(put("/alerts/1")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.alertId").value(1L));

        verify(alertService).updateAlert(any(Long.class), any(UpdateAlertRequest.class));
    }

    @Test
    void deleteAlert_existingAlert_returnsNoContent() throws Exception {
        // when & then
        mockMvc.perform(delete("/alerts/1"))
            .andExpect(status().isNoContent());

        verify(alertService).deleteAlert(1L);
    }

    private AlertResponse alertResponse() {
        AlertResponse response = new AlertResponse();
        response.setAlertId(1L);
        response.setTrackerId("tracker-001");
        response.setVehicleId("vehicle-001");
        response.setAlertType(AlertType.OVERSPEED);
        response.setSeverity(AlertSeverity.WARNING);
        response.setMessage("overspeed detected");
        response.setTriggeredAt(FIXED_CLOCK.instant());
        response.setAcknowledged(false);
        response.setResolved(false);
        return response;
    }
}