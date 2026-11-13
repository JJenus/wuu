package com.surf.tracker.core.api;

import com.surf.tracker.main.api.ApiV1;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

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
@ApiV1
@RequestMapping("/vehicles")
@Tag(name = "Vehicles", description = "Tenant-owned fleet records and last persisted telemetry; a snapshot does not prove current device connectivity.")
public class VehicleController {

    private final VehicleQueryService vehicleQueryService;
    private final VehicleService vehicleService;

    public VehicleController(VehicleQueryService vehicleQueryService, VehicleService vehicleService) {
        this.vehicleQueryService = vehicleQueryService;
        this.vehicleService = vehicleService;
    }

    @GetMapping
    @Operation(operationId = "vehiclesList", summary = "List tenant vehicles", description = "Requires vehicles.read. Zero-based page defaults to 0, size to 20, sortBy to createdAt and sortDirection to DESC. Returns the project's PagedResponse with last persisted location where available; organization comes from the current session.")
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
    @Operation(operationId = "vehiclesGet", summary = "Get a tenant vehicle", description = "Requires vehicles.read. vehicleId is the business string identifier, not a database numeric ID. Returns the persisted fleet/telemetry snapshot; absent or inaccessible vehicles are not exposed.")
    @ApiResponse(responseCode = "200", description = "Tenant-authorized vehicle snapshot")
    @ApiResponse(responseCode = "404", description = "Vehicle not found in the authorized scope")
    public ResponseEntity<VehicleResponse> getVehicle(@PathVariable String vehicleId) {
        Vehicle vehicle = vehicleQueryService.getVehicleById(vehicleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vehicle not found"));
        return ResponseEntity.ok(toResponse(vehicle));
    }

    @PostMapping
    @Operation(operationId = "vehiclesCreate", summary = "Create a tenant vehicle", description = "Requires vehicles.write. Validates vehicle_id and optional model, license_plate and vin. Organization is assigned server-side. Tracker registration/assignment is a separate operation; creation does not establish live connectivity.")
    @ApiResponse(responseCode = "201", description = "Vehicle created")
    @ApiResponse(responseCode = "400", description = "Invalid vehicle fields or unsupported registration data")
    @ApiResponse(responseCode = "409", description = "Identifier or persistence uniqueness conflict")
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
    @Operation(operationId = "vehiclesUpdate", summary = "Update vehicle profile fields", description = "Requires vehicles.write. Updates model, license_plate and vin on the tenant-authorized vehicle. Omitted values are assigned as null by this controller; this is not a general patch or tracker reassignment endpoint.")
    @ApiResponse(responseCode = "404", description = "Vehicle not found in the authorized scope")
    @ApiResponse(responseCode = "200", description = "Updated vehicle profile")
    public ResponseEntity<VehicleResponse> updateVehicle(@PathVariable String vehicleId,
                                                         @jakarta.validation.Valid @RequestBody UpdateVehicleRequest request) {
        Vehicle updated = vehicleService.updateProfile(vehicleId, request);
        return ResponseEntity.ok(toResponse(updated));
    }

    @DeleteMapping("/{vehicleId}")
    @Operation(operationId = "vehiclesDelete", summary = "Delete a tenant vehicle", description = "Requires vehicles.write. Deletes through the vehicle service. Success is 204 with no body; related data constraints may prevent deletion.")
    @ApiResponse(responseCode = "404", description = "Vehicle not found in the authorized scope")
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
