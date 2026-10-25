package com.surf.tracker.core.application;

import com.surf.tracker.shared.pubsub.DeviceCommandBuilder;
import com.surf.tracker.shared.pubsub.DeviceCommandTransport;
import com.surf.tracker.core.domain.FuelCutRequestedEvent;
import com.surf.tracker.core.domain.entity.DeviceCommand;
import com.surf.tracker.core.domain.entity.Tracker;
import com.surf.tracker.core.domain.enums.CommandStatus;
import com.surf.tracker.core.domain.enums.CommandType;
import com.surf.tracker.core.infrastructure.repository.DeviceCommandRepository;
import com.surf.tracker.core.infrastructure.repository.TrackerRepository;
import com.surf.tracker.shared.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
public class FuelCutCommandHandler {

    private static final Logger logger = LoggerFactory.getLogger(FuelCutCommandHandler.class);

    private final DeviceCommandTransport transport;
    private final TrackerRepository trackerRepository;
    private final DeviceCommandRepository commandRepository;
    private final List<DeviceCommandBuilder> builders;
    private final Clock clock;

    public FuelCutCommandHandler(
            DeviceCommandTransport transport,
            TrackerRepository trackerRepository,
            DeviceCommandRepository commandRepository,
            List<DeviceCommandBuilder> builders,
            Clock clock) {
        this.transport = transport;
        this.trackerRepository = trackerRepository;
        this.commandRepository = commandRepository;
        this.builders = builders;
        this.clock = clock;
    }

    @EventListener
    public void onFuelCutRequested(FuelCutRequestedEvent event) {
        try {
            logger.info("Handling fuel cut request for vehicle {}", event.getVehicleId());

            String deviceId = resolveDeviceId(event);
            if (deviceId == null) {
                logger.warn("No device found for vehicle {}", event.getVehicleId());
                return;
            }

            Optional<Tracker> trackerOpt = trackerRepository.findByDeviceIdAndOrganizationId(deviceId, TenantContext.getCurrentOrgId());
            String protocol = trackerOpt.map(Tracker::getProtocol).orElse("GT06");
            String command = builders.stream()
                .filter(b -> b.supports(protocol))
                .findFirst()
                .map(b -> b.buildFuelCutCommand(deviceId))
                .orElseThrow(() -> new IllegalStateException("No command builder for protocol " + protocol));

            DeviceCommand cmd = new DeviceCommand();
            cmd.setTracker(trackerOpt.orElse(null));
            cmd.setCommandType(CommandType.FUEL_CUT);
            cmd.setCommandData(command);
            cmd.setStatus(CommandStatus.PENDING);
            cmd.setInitiatedBy("system");
            cmd.setCreatedAt(clock.instant());
            commandRepository.save(cmd);

            transport.sendCommand(deviceId, command)
                .doOnSuccess(success -> {
                    Instant now = clock.instant();
                    if (Boolean.TRUE.equals(success)) {
                        cmd.setStatus(CommandStatus.SENT);
                        cmd.setSentAt(now);
                    } else {
                        cmd.setStatus(CommandStatus.FAILED);
                        cmd.setErrorMessage("Device not connected");
                    }
                    commandRepository.save(cmd);
                })
                .doOnError(err -> {
                    cmd.setStatus(CommandStatus.FAILED);
                    cmd.setErrorMessage(err.getMessage());
                    commandRepository.save(cmd);
                })
                .subscribe();

        } catch (Exception e) {
            logger.error("Failed to handle fuel cut for vehicle {}", event.getVehicleId(), e);
        }
    }

    private String resolveDeviceId(FuelCutRequestedEvent event) {
        if (event.getDeviceId() != null && !event.getDeviceId().isBlank() && !"unknown".equals(event.getDeviceId())) {
            return event.getDeviceId();
        }
        return trackerRepository.findByVehicleId(event.getVehicleId(), TenantContext.getCurrentOrgId())
                .map(Tracker::getDeviceId)
                .orElse(null);
    }
}
