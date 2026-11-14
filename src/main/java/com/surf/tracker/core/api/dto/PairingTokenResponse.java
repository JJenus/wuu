package com.surf.tracker.core.api.dto;

import java.time.Instant;

@io.swagger.v3.oas.annotations.media.Schema(description = "Short-lived credential that lets one phone or browser report as this tracker. Shown once; it is not stored.")
public record PairingTokenResponse(String trackerId, String deviceId, String token, Instant expiresAt, String pairingUrl) {
}
