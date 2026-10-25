package com.surf.tracker.core.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.surf.tracker.core.api.dto.CreateDeviceCommandRequest;
import com.surf.tracker.core.application.service.DeviceCommandService;
import com.surf.tracker.core.domain.entity.DeviceCommand;
import com.surf.tracker.core.domain.entity.Tracker;
import com.surf.tracker.core.domain.enums.CommandStatus;
import com.surf.tracker.core.domain.enums.CommandType;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DeviceCommandControllerTest {

    @Mock
    private DeviceCommandService deviceCommandService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        com.surf.tracker.shared.security.TenantContext.setCurrentUserId(42L);
        mockMvc = MockMvcBuilders.standaloneSetup(new DeviceCommandController(deviceCommandService)).build();
    }

    @org.junit.jupiter.api.AfterEach
    void cleanUp() { com.surf.tracker.shared.security.TenantContext.clear(); }

    private DeviceCommand aCommand() {
        Tracker tracker = new Tracker();
        tracker.setTrackerId("TRK-001");
        DeviceCommand command = new DeviceCommand();
        command.setCommandId(11L);
        command.setTracker(tracker);
        command.setCommandType(CommandType.FUEL_CUT);
        command.setStatus(CommandStatus.PENDING);
        return command;
    }

    @Test
    void listCommands_returnsPagedContent() throws Exception {
        when(deviceCommandService.getCommandsByTrackerPaged(eq("TRK-001"), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(aCommand())));

        mockMvc.perform(get("/api/v1/commands").param("trackerId", "TRK-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].commandId", is(11)))
                .andExpect(jsonPath("$.content[0].trackerId", is("TRK-001")));
    }

    @Test
    void getCommand_whenExists_returnsCommand() throws Exception {
        when(deviceCommandService.getCommand(11L)).thenReturn(Optional.of(aCommand()));

        mockMvc.perform(get("/api/v1/commands/11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commandId", is(11)));
    }

    @Test
    void getCommand_whenMissing_returns404() throws Exception {
        when(deviceCommandService.getCommand(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/commands/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void createCommand_returnsCreated() throws Exception {
        when(deviceCommandService.createCommand("TRK-001", CommandType.FUEL_CUT, "data", "42"))
                .thenReturn(aCommand());

        CreateDeviceCommandRequest request = new CreateDeviceCommandRequest();
        request.setTrackerId("TRK-001");
        request.setCommandType(CommandType.FUEL_CUT);
        request.setCommandData("data");
        request.setInitiatedBy("admin");

        mockMvc.perform(post("/api/v1/commands")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.commandId", is(11)));
    }

    @Test
    void markSent_updatesCommand() throws Exception {
        doNothing().when(deviceCommandService).markCommandAsSent(11L);
        when(deviceCommandService.getCommand(11L)).thenReturn(Optional.of(aCommand()));

        mockMvc.perform(post("/api/v1/commands/11/send"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commandId", is(11)));

        verify(deviceCommandService).markCommandAsSent(11L);
    }
}
