package com.surf.tracker.shared.domain;

/** Protocol-neutral alarm and state flags a device can report. */
public enum DeviceAlarm {
    SOS, OVERSPEED, ROUTE_DEVIATION, VIBRATION, LOW_BATTERY, POWER_CUT,
    FENCE_IN, FENCE_OUT, DOOR_OPEN, OIL_CUT, BATTERY_REMOVED, TAMPER
}
