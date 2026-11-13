package com.surf.tracker.core.api.dto;

import jakarta.validation.constraints.Size;

/** Editable registration metadata only; device identity and measured status are not inputs. */
public record TrackerProfileRequest(
    @Size(max = 100) String model,
    @Size(max = 20) String protocol,
    @Size(max = 50) String firmwareVersion,
    @Size(max = 20) String simNumber) {}
