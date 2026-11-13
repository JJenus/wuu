package com.surf.tracker.devicecomm.protocol.model;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/** Device condition reported alongside a frame. Null numeric fields mean "not reported". */
public record DeviceHealth(Integer batteryPercent, Float batteryVolts, Integer gsmSignal, Integer gpsSignal,
                           Set<DeviceAlarm> alarms) {
    public static final DeviceHealth NONE = new DeviceHealth(null, null, null, null, Set.of());

    public DeviceHealth {
        if (batteryPercent != null && (batteryPercent < 0 || batteryPercent > 100))
            throw new IllegalArgumentException("Battery percent must be between 0 and 100");
        alarms = alarms == null || alarms.isEmpty()
                ? Set.of() : Collections.unmodifiableSet(EnumSet.copyOf(alarms));
    }

    public static DeviceHealth ofAlarms(Set<DeviceAlarm> alarms) {
        return new DeviceHealth(null, null, null, null, alarms);
    }

    public static DeviceHealth ofBattery(Integer percent, Float volts) {
        return new DeviceHealth(percent, volts, null, null, Set.of());
    }

    public boolean hasAlarms() {
        return !alarms.isEmpty();
    }
}
