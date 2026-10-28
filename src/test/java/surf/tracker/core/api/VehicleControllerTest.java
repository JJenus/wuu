package com.surf.tracker.core.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.surf.tracker.core.api.dto.CreateVehicleRequest;
import com.surf.tracker.core.api.dto.UpdateVehicleRequest;
import com.surf.tracker.core.application.VehicleQueryService;
import com.surf.tracker.core.application.service.VehicleService;
import com.surf.tracker.core.domain.entity.Vehicle;
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

import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class VehicleControllerTest {

    @Mock
    private VehicleQueryService vehicleQueryService;

    @Mock
    private VehicleService vehicleService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new VehicleController(vehicleQueryService, vehicleService)).build();
    }

    private Vehicle aVehicle() {
        Vehicle vehicle = new Vehicle();
        vehicle.setVehicleId("VEH-001");
        vehicle.setDeviceId("DEV-001");
        vehicle.setModel("Truck");
        vehicle.setLicensePlate("ABC-123");
        vehicle.setVin("VIN-9");
        vehicle.setOrganizationId(1L);
        return vehicle;
    }

    @Test
    void listVehicles_returnsPagedContent() throws Exception {
        when(vehicleQueryService.getVehicles(any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(aVehicle())));

        mockMvc.perform(get("/api/v1/vehicles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].vehicleId", is("VEH-001")))
                .andExpect(jsonPath("$.content[0].model", is("Truck")))
                .andExpect(jsonPath("$.totalElements", is(1)));
    }

    @Test
    void getVehicle_whenExists_returnsVehicle() throws Exception {
        when(vehicleQueryService.getVehicleById("VEH-001")).thenReturn(Optional.of(aVehicle()));

        mockMvc.perform(get("/api/v1/vehicles/VEH-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vehicleId", is("VEH-001")));
    }

    @Test
    void getVehicle_whenMissing_returns404() throws Exception {
        when(vehicleQueryService.getVehicleById("VEH-999")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/vehicles/VEH-999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void createVehicle_returnsCreated() throws Exception {
        when(vehicleService.createVehicle(any())).thenReturn(aVehicle());

        CreateVehicleRequest request = new CreateVehicleRequest();
        request.setVehicleId("VEH-001");
        request.setModel("Truck");

        mockMvc.perform(post("/api/v1/vehicles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.vehicleId", is("VEH-001")));

        verify(vehicleService).createVehicle(any());
    }

    @Test
    void updateVehicle_returnsUpdated() throws Exception {
        when(vehicleService.getVehicle("VEH-001")).thenReturn(Optional.of(aVehicle()));
        when(vehicleService.saveVehicle(any())).thenReturn(aVehicle());

        UpdateVehicleRequest request = new UpdateVehicleRequest();
        request.setModel("Van");

        mockMvc.perform(put("/api/v1/vehicles/VEH-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vehicleId", is("VEH-001")));
    }

    @Test
    void deleteVehicle_whenExists_returnsNoContent() throws Exception {
        doNothing().when(vehicleService).deleteVehicle("VEH-001");

        mockMvc.perform(delete("/api/v1/vehicles/VEH-001"))
                .andExpect(status().isNoContent());

        verify(vehicleService).deleteVehicle("VEH-001");
    }

    @Test
    void deleteVehicle_whenMissing_returns404() throws Exception {
        doThrow(new IllegalArgumentException("Vehicle not found: VEH-999"))
                .when(vehicleService).deleteVehicle("VEH-999");

        mockMvc.perform(delete("/api/v1/vehicles/VEH-999"))
                .andExpect(status().isNotFound());
    }
}
