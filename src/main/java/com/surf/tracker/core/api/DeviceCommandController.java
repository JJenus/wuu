package com.surf.tracker.core.api;

import com.surf.tracker.main.api.ApiV1;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.surf.tracker.core.api.dto.*;
import com.surf.tracker.core.application.service.DeviceCommandService;
import com.surf.tracker.core.domain.entity.DeviceCommand;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@ApiV1
@RequestMapping("/commands")
@Tag(name = "Device Commands", description = "Persisted device commands. Creation is not proof of transmission, delivery or hardware acknowledgement.")
public class DeviceCommandController {

    private final DeviceCommandService deviceCommandService;

    public DeviceCommandController(DeviceCommandService deviceCommandService) {
        this.deviceCommandService = deviceCommandService;
    }

    @GetMapping
    @Operation(operationId = "commandsList", summary = "List a tracker's commands", description = "Requires devices.read and required trackerId. Zero-based page defaults to 0, size to 20, sortBy to createdAt and sortDirection to DESC. Returns the project's PagedResponse for the authorized tracker.")
    public ResponseEntity<PagedResponse<DeviceCommandResponse>> listCommands(
            @RequestParam String trackerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") Sort.Direction sortDirection) {

        Page<DeviceCommand> commands = deviceCommandService.getCommandsByTrackerPaged(
                trackerId, PageRequest.of(page, size, Sort.by(sortDirection, sortBy)));
        return ResponseEntity.ok(new PagedResponse<>(commands.map(this::toResponse)));
    }

    @GetMapping("/{commandId}")
    @Operation(operationId = "commandsGet", summary = "Get a command record", description = "Requires devices.read. commandId is numeric. Reads saved status, attempts and response metadata; does not contact the device.")
    @ApiResponse(responseCode = "404", description = "Command not found in the authorized scope")
    @ApiResponse(responseCode = "200", description = "Persisted command state")
    public ResponseEntity<DeviceCommandResponse> getCommand(@PathVariable Long commandId) {
        DeviceCommand command = deviceCommandService.getCommand(commandId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Command not found"));
        return ResponseEntity.ok(toResponse(command));
    }

    @PostMapping
    @Operation(operationId = "commandsCreate", summary = "Create a device command record", description = "Requires devices.command. Accepts tracker_id, command_type and command_data. initiated_by is derived from the authenticated user, never from a caller-selected actor. Creation does not guarantee transport or delivery; the backend transport remains independently constrained.")
    @ApiResponse(responseCode = "201", description = "Command record created")
    public ResponseEntity<DeviceCommandResponse> createCommand(@RequestBody CreateDeviceCommandRequest request) {
        DeviceCommand created = deviceCommandService.createCommand(
                request.getTrackerId(), request.getCommandType(), request.getCommandData(),
                String.valueOf(com.surf.tracker.shared.security.TenantContext.getCurrentUserId()));
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(created));
    }

    @PostMapping("/{commandId}/send")
    @Operation(operationId = "commandsMarkSent", summary = "Disabled HTTP command transition: sent", description = "SecurityConfig denies this HTTP operation for every caller, including administrators. It is not a supported command transport API; do not use it to simulate hardware transmission.", responses = @ApiResponse(responseCode = "403", description = "HTTP transition explicitly disabled"))
    public ResponseEntity<DeviceCommandResponse> markSent(@PathVariable Long commandId) {
        deviceCommandService.markCommandAsSent(commandId);
        return ok(commandId);
    }

    @PostMapping("/{commandId}/deliver")
    @Operation(operationId = "commandsMarkDelivered", summary = "Disabled HTTP command transition: delivered", description = "SecurityConfig denies this HTTP operation for every caller. Optional response is not accepted as a trusted device acknowledgement through HTTP.", responses = @ApiResponse(responseCode = "403", description = "HTTP transition explicitly disabled"))
    public ResponseEntity<DeviceCommandResponse> markDelivered(@PathVariable Long commandId,
                                                               @RequestParam(required = false) String response) {
        deviceCommandService.markCommandAsDelivered(commandId, response);
        return ok(commandId);
    }

    @PostMapping("/{commandId}/fail")
    @Operation(operationId = "commandsMarkFailed", summary = "Disabled HTTP command transition: failed", description = "SecurityConfig denies this HTTP operation for every caller. Command failures must originate from a trusted transport path, not a caller-supplied error parameter.", responses = @ApiResponse(responseCode = "403", description = "HTTP transition explicitly disabled"))
    public ResponseEntity<DeviceCommandResponse> markFailed(@PathVariable Long commandId,
                                                            @RequestParam(required = false) String error) {
        deviceCommandService.markCommandAsFailed(commandId, error);
        return ok(commandId);
    }

    private ResponseEntity<DeviceCommandResponse> ok(Long commandId) {
        DeviceCommand command = deviceCommandService.getCommand(commandId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Command not found"));
        return ResponseEntity.ok(toResponse(command));
    }

    private DeviceCommandResponse toResponse(DeviceCommand command) {
        DeviceCommandResponse response = new DeviceCommandResponse();
        response.setCommandId(command.getCommandId());
        response.setTrackerId(command.getTracker() != null ? command.getTracker().getTrackerId() : null);
        response.setCommandType(command.getCommandType() != null ? command.getCommandType().name() : null);
        response.setCommandData(command.getCommandData());
        response.setStatus(command.getStatus() != null ? command.getStatus().name() : null);
        response.setResponseData(command.getResponseData());
        response.setErrorMessage(command.getErrorMessage());
        response.setRetryCount(command.getRetryCount());
        response.setMaxRetries(command.getMaxRetries());
        response.setInitiatedBy(command.getInitiatedBy());
        response.setSentAt(command.getSentAt());
        response.setRespondedAt(command.getRespondedAt());
        response.setCreatedAt(command.getCreatedAt());
        response.setUpdatedAt(command.getUpdatedAt());
        return response;
    }
}
