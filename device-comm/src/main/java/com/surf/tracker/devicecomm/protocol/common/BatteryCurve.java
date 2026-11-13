package com.surf.tracker.devicecomm.protocol.common;

import java.util.Optional;

/** Converts a raw 4-hex-digit battery reading into volts and percent for one device family. */
public record BatteryCurve(float rawFullScale, float fullScaleVolts, float nominalVolts) {
    public record Reading(float volts, int percent) {
    }

    public BatteryCurve {
        if (rawFullScale <= 0 || fullScaleVolts <= 0 || nominalVolts <= 0)
            throw new IllegalArgumentException("Battery curve parameters must be positive");
    }

    public Optional<Reading> fromHex(String hex) {
        if (hex == null || !hex.matches("[0-9A-Fa-f]{4}")) return Optional.empty();
        float volts = (Integer.parseInt(hex, 16) / rawFullScale) * fullScaleVolts;
        float percent = Math.min(100.0f, Math.max(1.0f, (volts / nominalVolts) * 100.0f));
        return Optional.of(new Reading(volts, Math.round(percent)));
    }
}
