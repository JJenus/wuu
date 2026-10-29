package com.surf.tracker.subscription.api.dto;

public record UsageRequest(String operationKey, String meter, Long units) { }
