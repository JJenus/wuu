package com.surf.tracker.devicecomm.protocol.common;

import com.surf.tracker.shared.domain.DeviceAlarm;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Declarative decoding of a hex status word into alarms. A protocol declares which bit means which
 * alarm; no hand-written bit twiddling per protocol.
 */
public final class StatusBitMap {
    public record Bit(int index, DeviceAlarm alarm) {
        public Bit {
            if (index < 0 || index > 31) throw new IllegalArgumentException("Status bit must be 0..31");
        }
    }

    private final boolean activeLow;
    private final List<Bit> bits;

    private StatusBitMap(boolean activeLow, List<Bit> bits) {
        this.activeLow = activeLow;
        this.bits = List.copyOf(bits);
    }

    /** Index 0 is the least significant bit; a bit value of 0 means the alarm is active. */
    public static StatusBitMap activeLow(Bit... bits) {
        return new StatusBitMap(true, List.of(bits));
    }

    /** Index 0 is the least significant bit; a bit value of 1 means the alarm is active. */
    public static StatusBitMap activeHigh(Bit... bits) {
        return new StatusBitMap(false, List.of(bits));
    }

    public static Bit bit(int index, DeviceAlarm alarm) {
        return new Bit(index, alarm);
    }

    /** Returns the active alarms; a missing or malformed status word reports none rather than guessing. */
    public Set<DeviceAlarm> decode(String hex) {
        Set<DeviceAlarm> active = EnumSet.noneOf(DeviceAlarm.class);
        if (hex == null || !hex.matches("[0-9A-Fa-f]{1,8}")) return active;
        long value = Long.parseLong(hex, 16);
        for (Bit bit : bits) {
            boolean set = ((value >> bit.index()) & 1L) == 1L;
            if (set != activeLow) active.add(bit.alarm());
        }
        return active;
    }
}
