package com.surf.tracker.core.application.service;

import com.surf.tracker.core.domain.entity.*;
import com.surf.tracker.core.domain.enums.CommandStatus;
import com.surf.tracker.core.domain.enums.CommandType;
import com.surf.tracker.core.infrastructure.repository.*;
import com.surf.tracker.shared.security.TenantContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class DeviceCommandService {
    @Autowired
    private DeviceCommandRepository commandRepository;
    @Autowired
    private TrackerRepository trackerRepository;
    private final Clock clock;

    public DeviceCommandService(
            DeviceCommandRepository commandRepository,
            TrackerRepository trackerRepository,
            Clock clock) {
        this.commandRepository = commandRepository;
        this.trackerRepository = trackerRepository;
        this.clock = clock;
    }

    @Transactional
    public DeviceCommand createCommand(String trackerId, CommandType commandType,
                                       String commandData, String initiatedBy) {

        Long orgId = TenantContext.getCurrentOrgId();
        Tracker tracker = trackerRepository.findByIdAndOrganizationId(trackerId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Tracker not found: " + trackerId));

        DeviceCommand command = new DeviceCommand();
        command.setTracker(tracker);
        command.setCommandType(commandType);
        command.setCommandData(commandData);
        command.setStatus(CommandStatus.PENDING);
        command.setInitiatedBy(initiatedBy);
        command.setCreatedAt(clock.instant());

        return commandRepository.save(command);
    }

    @Transactional
    public void updateCommandStatus(Long commandId, CommandStatus status,
                                    String responseData, String errorMessage) {

        DeviceCommand command = commandRepository.findByIdAndOrganizationId(commandId, TenantContext.getCurrentOrgId())
                .orElseThrow(() -> new IllegalArgumentException("Command not found"));

        command.setStatus(status);
        command.setUpdatedAt(clock.instant());

        if (responseData != null) {
            command.setResponseData(responseData);
            command.setRespondedAt(clock.instant());
        }

        if (errorMessage != null) {
            command.setErrorMessage(errorMessage);
        }

        commandRepository.save(command);
    }

    @Transactional
    public void markCommandAsSent(Long commandId) {
        DeviceCommand command = commandRepository.findByIdAndOrganizationId(commandId, TenantContext.getCurrentOrgId())
                .orElseThrow(() -> new IllegalArgumentException("Command not found"));

        command.markAsSent();
        commandRepository.save(command);
    }

    @Transactional
    public void markCommandAsDelivered(Long commandId, String response) {
        DeviceCommand command = commandRepository.findByIdAndOrganizationId(commandId, TenantContext.getCurrentOrgId())
                .orElseThrow(() -> new IllegalArgumentException("Command not found"));

        command.markAsDelivered(response);
        commandRepository.save(command);
    }

    @Transactional
    public void markCommandAsFailed(Long commandId, String error) {
        DeviceCommand command = commandRepository.findByIdAndOrganizationId(commandId, TenantContext.getCurrentOrgId())
                .orElseThrow(() -> new IllegalArgumentException("Command not found"));

        command.markAsFailed(error);
        commandRepository.save(command);
    }

    @Transactional(readOnly = true)
    public List<DeviceCommand> getPendingCommands() {
        return commandRepository.findByStatusAndOrganizationId(CommandStatus.PENDING,
                        TenantContext.getCurrentOrgId(), Pageable.unpaged())
                .getContent();
    }

    @Transactional(readOnly = true)
    public List<DeviceCommand> getCommandsByTracker(String trackerId) {
        return commandRepository.findByTrackerTrackerIdAndOrganizationId(trackerId,
                        TenantContext.getCurrentOrgId(), Pageable.unpaged())
                .getContent();
    }

    @Transactional(readOnly = true)
    public Page<DeviceCommand> getCommandsByTrackerPaged(String trackerId, Pageable pageable) {
        return commandRepository.findByTrackerTrackerIdAndOrganizationId(trackerId,
                        TenantContext.getCurrentOrgId(), pageable);
    }

    @Transactional(readOnly = true)
    public Optional<DeviceCommand> getCommand(Long commandId) {
        return commandRepository.findByIdAndOrganizationId(commandId, TenantContext.getCurrentOrgId());
    }

    @Transactional
    public void cleanupOldCommands(int daysToKeep) {
        Instant cutoffTime = clock.instant().minusSeconds(daysToKeep * 24 * 60 * 60);
        commandRepository.cleanupOldCommands(cutoffTime);
    }
}