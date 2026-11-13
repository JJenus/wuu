package com.surf.tracker.core.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Both identities are explicit so an editor cannot silently overwrite a newer assignment. */
public record ReassignTrackerRequest(
    @NotBlank @Size(max = 50) String sourceVehicleId,
    @NotBlank @Size(max = 50) String targetVehicleId) {}
