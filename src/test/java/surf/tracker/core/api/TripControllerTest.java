package com.surf.tracker.core.api;

import com.surf.tracker.core.application.TripQueryService;
import com.surf.tracker.core.domain.entity.Trip;
import com.surf.tracker.core.domain.entity.Vehicle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TripControllerTest {

    @Mock
    private TripQueryService tripQueryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TripController(tripQueryService)).build();
    }

    private Trip aTrip() {
        Vehicle vehicle = new Vehicle();
        vehicle.setVehicleId("VEH-001");
        Trip trip = new Trip();
        trip.setTripId("TRIP-001");
        trip.setVehicle(vehicle);
        trip.setStartTime(Instant.parse("2026-01-01T10:00:00Z"));
        trip.setIsActive(true);
        return trip;
    }

    @Test
    void listTrips_returnsPagedContent() throws Exception {
        when(tripQueryService.getTrips(any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(aTrip())));

        mockMvc.perform(get("/api/v1/trips"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].tripId", is("TRIP-001")))
                .andExpect(jsonPath("$.content[0].vehicleId", is("VEH-001")));
    }

    @Test
    void getTrip_whenExists_returnsTrip() throws Exception {
        when(tripQueryService.getTripById("TRIP-001")).thenReturn(Optional.of(aTrip()));

        mockMvc.perform(get("/api/v1/trips/TRIP-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tripId", is("TRIP-001")));
    }

    @Test
    void getTrip_whenMissing_returns404() throws Exception {
        when(tripQueryService.getTripById("TRIP-999")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/trips/TRIP-999"))
                .andExpect(status().isNotFound());
    }
}
