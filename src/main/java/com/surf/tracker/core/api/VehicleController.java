package com.surf.tracker.core.api;

import com.surf.tracker.core.api.dto.*;
import com.surf.tracker.core.application.VehicleQueryService;
import com.surf.tracker.core.application.service.VehicleService;
import com.surf.tracker.core.domain.entity.TrackerLocation;
import com.surf.tracker.core.domain.entity.Vehicle;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

@RestController
@RequestMapping("/api/v1/vehicles")
public class VehicleController {

    private final VehicleQueryService vehicleQueryService;
    private final VehicleService vehicleService;

    public VehicleController(VehicleQueryService vehicleQueryService, VehicleService vehicleService) {
        this.vehicleQueryService = vehicleQueryService;
        this.vehicleService = vehicleService;
    }

    @GetMapping
    public ResponseEntity<PagedResponse<VehicleResponse>> listVehicles(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") Sort.Direction sortDirection) {

        Page<Vehicle> vehicles = vehicleQueryService.getVehicles(
                PageRequest.of(page, size, Sort.by(sortDirection, sortBy)));
        return ResponseEntity.ok(new PagedResponse<>(vehicles.map(this::toResponse)));
    }

    @GetMapping("/{vehicleId}")
    public ResponseEntity<VehicleResponse> getVehicle(@PathVariable String vehicleId) {
        Vehicle vehicle = vehicleQueryService.getVehicleById(vehicleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vehicle not found"));
        return ResponseEntity.ok(toResponse(vehicle));
    }

    @PostMapping
    public ResponseEntity<VehicleResponse> createVehicle(@jakarta.validation.Valid @RequestBody CreateVehicleRequest request) {
        Vehicle vehicle = new Vehicle();
        vehicle.setVehicleId(request.getVehicleId());
        vehicle.setDeviceId(request.getDeviceId());
        vehicle.setModel(request.getModel());
        vehicle.setLicensePlate(request.getLicensePlate());
        vehicle.setVin(request.getVin());

        try {
            Vehicle created = vehicleService.createVehicle(vehicle);
            return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(created));
        } catch (org.springframework.dao.DataIntegrityViolationException
                 | jakarta.persistence.EntityExistsException
                 | org.hibernate.exception.ConstraintViolationException conflict) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Vehicle conflicts with existing data");
        }
    }

    @PutMapping("/{vehicleId}")
    public ResponseEntity<VehicleResponse> updateVehicle(@PathVariable String vehicleId,
                                                         @jakarta.validation.Valid @RequestBody UpdateVehicleRequest request) {
        Vehicle vehicle = vehicleService.getVehicle(vehicleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vehicle not found"));

        vehicle.setModel(request.getModel());
        vehicle.setLicensePlate(request.getLicensePlate());
        vehicle.setVin(request.getVin());

        Vehicle updated = vehicleService.saveVehicle(vehicle);
        return ResponseEntity.ok(toResponse(updated));
    }

    @DeleteMapping("/{vehicleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteVehicle(@PathVariable String vehicleId) {
        try {
            vehicleService.deleteVehicle(vehicleId);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Vehicle not found");
        }
    }

    private VehicleResponse toResponse(Vehicle vehicle) {
        VehicleResponse response = new VehicleResponse();
        response.setVehicleId(vehicle.getVehicleId());
        response.setDeviceId(vehicle.getDeviceId());
        response.setModel(vehicle.getModel());
        response.setLicensePlate(vehicle.getLicensePlate());
        response.setVin(vehicle.getVin());
        response.setEngineState(Optional.ofNullable(vehicle.getEngineState()).map(Enum::name).orElse(null));
        response.setAccStatus(vehicle.getAccStatus());
        response.setFuelLevel(vehicle.getFuelLevel());
        response.setOdometerKm(vehicle.getOdometerKm());
        response.setFuelCutActive(vehicle.getFuelCutActive());
        response.setOrganizationId(vehicle.getOrganizationId());
        response.setLastTelemetryTime(vehicle.getLastTelemetryTime());

        TrackerLocation loc = vehicle.getCurrentLocation();
        if (loc != null) {
            LocationResponse locResp = new LocationResponse();
            locResp.setLatitude(loc.getLatitude());
            locResp.setLongitude(loc.getLongitude());
            locResp.setSpeedKmh(loc.getSpeedKmh());
            locResp.setRecordedAt(loc.getRecordedAt());
            response.setCurrentLocation(locResp);
        }
        return response;
    }
}
