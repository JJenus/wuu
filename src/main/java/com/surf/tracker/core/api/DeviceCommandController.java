package com.surf.tracker.core.api;

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
@RequestMapping("/api/v1/commands")
public class DeviceCommandController {

    private final DeviceCommandService deviceCommandService;

    public DeviceCommandController(DeviceCommandService deviceCommandService) {
        this.deviceCommandService = deviceCommandService;
    }

    @GetMapping
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
    public ResponseEntity<DeviceCommandResponse> getCommand(@PathVariable Long commandId) {
        DeviceCommand command = deviceCommandService.getCommand(commandId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Command not found"));
        return ResponseEntity.ok(toResponse(command));
    }

    @PostMapping
    public ResponseEntity<DeviceCommandResponse> createCommand(@RequestBody CreateDeviceCommandRequest request) {
        DeviceCommand created = deviceCommandService.createCommand(
                request.getTrackerId(), request.getCommandType(), request.getCommandData(),
                String.valueOf(com.surf.tracker.shared.security.TenantContext.getCurrentUserId()));
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(created));
    }

    @PostMapping("/{commandId}/send")
    public ResponseEntity<DeviceCommandResponse> markSent(@PathVariable Long commandId) {
        deviceCommandService.markCommandAsSent(commandId);
        return ok(commandId);
    }

    @PostMapping("/{commandId}/deliver")
    public ResponseEntity<DeviceCommandResponse> markDelivered(@PathVariable Long commandId,
                                                               @RequestParam(required = false) String response) {
        deviceCommandService.markCommandAsDelivered(commandId, response);
        return ok(commandId);
    }

    @PostMapping("/{commandId}/fail")
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
