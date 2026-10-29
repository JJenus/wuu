package com.surf.tracker.subscription.api.dto;

public record CreateEnrollmentRequest(String planId, Long version, String operationKey) {
}
