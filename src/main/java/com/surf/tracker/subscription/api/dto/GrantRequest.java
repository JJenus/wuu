package com.surf.tracker.subscription.api.dto;

import java.time.Instant;

public record GrantRequest(String operationKey, String kind, String reason, Instant start, Instant end, Long units) { }
