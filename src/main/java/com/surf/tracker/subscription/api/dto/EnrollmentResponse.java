package com.surf.tracker.subscription.api.dto;

import java.util.UUID;

public record EnrollmentResponse(UUID id, UUID planId, long version, String status, long remainingUnits) {
}
