package com.surf.tracker.subscription.api.dto;

@io.swagger.v3.oas.annotations.media.Schema(description = "Pending enrollment with an owner-scoped operation key for replay. Does not assert payment success.", example = "{\"plan_id\":\"adbaab3a-7c4d-4c02-bbf9-3b9d22dd1d02\",\"version\":1,\"operation_key\":\"enroll-2026-11-12-01\"}")
public record CreateEnrollmentRequest(String planId, Long version, String operationKey) {
}
