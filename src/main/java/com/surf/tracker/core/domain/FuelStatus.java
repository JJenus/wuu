package com.surf.tracker.core.domain;

import java.time.Instant;

public record FuelStatus(
    float currentLevel,
    float lastConsumption,
    Instant lastUpdated
) {
    public boolean isLow() {
        return currentLevel < 15.0f;
    }
    
    public boolean isEmpty() {
        return currentLevel < 5.0f;
    }
}
