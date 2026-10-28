package com.surf.tracker.core.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.surf.tracker.core.api.dto.CreateTrackerRequest;
import com.surf.tracker.core.application.service.TrackerService;
import com.surf.tracker.core.domain.entity.Tracker;
import com.surf.tracker.core.domain.entity.Vehicle;
import com.surf.tracker.core.domain.enums.TrackerStatus;
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
import java.util.Optional;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TrackerControllerTest {

    @Mock
    private TrackerService trackerService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TrackerController(trackerService, clock)).build();
    }

    private Tracker aTracker() {
        Vehicle vehicle = new Vehicle();
        vehicle.setVehicleId("VEH-001");
        Tracker tracker = new Tracker();
        tracker.setTrackerId("TRK-001");
        tracker.setDeviceId("DEV-001");
        tracker.setStatus(TrackerStatus.ACTIVE);
        tracker.setIsOnline(true);
        tracker.setVehicle(vehicle);
        return tracker;
    }

    @Test
    void listTrackers_returnsPagedContent() throws Exception {
        when(trackerService.getTrackers(any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(aTracker())));

        mockMvc.perform(get("/api/v1/trackers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].trackerId", is("TRK-001")))
                .andExpect(jsonPath("$.content[0].vehicleId", is("VEH-001")));
    }

    @Test
    void listTrackers_whenStatusFilter_delegatesToStatus() throws Exception {
        when(trackerService.getByStatus(eq(TrackerStatus.ACTIVE), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(aTracker())));

        mockMvc.perform(get("/api/v1/trackers").param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status", is("ACTIVE")));

        verify(trackerService).getByStatus(eq(TrackerStatus.ACTIVE), any(PageRequest.class));
    }

    @Test
    void getTracker_whenExists_returnsTracker() throws Exception {
        when(trackerService.getTrackerById("TRK-001")).thenReturn(Optional.of(aTracker()));

        mockMvc.perform(get("/api/v1/trackers/TRK-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackerId", is("TRK-001")));
    }

    @Test
    void getTracker_whenMissing_returns404() throws Exception {
        when(trackerService.getTrackerById("TRK-999")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/trackers/TRK-999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void createTracker_returnsCreated() throws Exception {
        when(trackerService.createTracker(any(), eq("VEH-001"))).thenReturn(aTracker());

        CreateTrackerRequest request = new CreateTrackerRequest();
        request.setTrackerId("TRK-001");
        request.setDeviceId("DEV-001");
        request.setVehicleId("VEH-001");

        mockMvc.perform(post("/api/v1/trackers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.trackerId", is("TRK-001")));
    }

    @Test
    void deleteTracker_whenMissing_returns404() throws Exception {
        doThrow(new IllegalArgumentException("Tracker not found: TRK-999"))
                .when(trackerService).deleteTracker("TRK-999");

        mockMvc.perform(delete("/api/v1/trackers/TRK-999"))
                .andExpect(status().isNotFound());
    }
}
