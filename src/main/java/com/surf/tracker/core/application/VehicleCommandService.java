package com.surf.tracker.core.application;

import com.surf.tracker.core.domain.FuelCutRequestedEvent;
import com.surf.tracker.core.domain.entity.Vehicle;
import com.surf.tracker.core.exception.VehicleException;
import com.surf.tracker.core.infrastructure.repository.VehicleRepository;
import com.surf.tracker.shared.exception.ValidationException;
import com.surf.tracker.shared.pubsub.EventPublisher;
import com.surf.tracker.shared.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
public class VehicleCommandService {
    private static final Logger logger = LoggerFactory.getLogger(VehicleCommandService.class);

    private final VehicleRepository vehicleRepository;
    private final EventPublisher eventPublisher;
    private final Clock clock;

    public VehicleCommandService(VehicleRepository vehicleRepository,
                                 EventPublisher eventPublisher,
                                 Clock clock) {
        this.vehicleRepository = vehicleRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public void handleFuelCutRequest(String vehicleId) {
        try {
            Long orgId = TenantContext.getCurrentOrgId();
            Vehicle vehicle = vehicleRepository.findByIdAndOrganizationId(vehicleId, orgId)
                .orElseThrow(() -> VehicleException.notFound(vehicleId));

            Instant now = clock.instant();
            vehicle.issueFuelCutOffCommand(now);
            vehicleRepository.save(vehicle);

            FuelCutRequestedEvent event = new FuelCutRequestedEvent(
                clock,
                vehicle.getVehicleId(),
                vehicle.getDeviceId()
            );
            eventPublisher.publish(event);

            logger.info("Fuel cut command processed for vehicle {}", vehicleId);

        } catch (VehicleException e) {
            logger.warn("Fuel cut validation failed for vehicle {}: {}", vehicleId, e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("Failed to process fuel cut for vehicle {}", vehicleId, e);
            throw new ValidationException("FUEL_CUT_PROCESS_ERROR",
                "Failed to process fuel cut for vehicle " + vehicleId, e);
        }
    }

    @Transactional
    public void handleFuelRestoreRequest(String vehicleId) {
        try {
            Long orgId = TenantContext.getCurrentOrgId();
            Vehicle vehicle = vehicleRepository.findByIdAndOrganizationId(vehicleId, orgId)
                .orElseThrow(() -> VehicleException.notFound(vehicleId));

            Instant now = clock.instant();
            vehicle.issueFuelRestoreCommand(now);
            vehicleRepository.save(vehicle);

            logger.info("Fuel restore command processed for vehicle {}", vehicleId);

        } catch (VehicleException e) {
            logger.warn("Fuel restore failed for vehicle {}: {}", vehicleId, e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("Failed to restore fuel for vehicle {}", vehicleId, e);
            throw new ValidationException("FUEL_RESTORE_PROCESS_ERROR",
                "Failed to restore fuel for vehicle " + vehicleId, e);
        }
    }
}
