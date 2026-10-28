package com.surf.tracker.devicecomm.infrastructure;

public record DeviceStatus(
        boolean gpsLocated,
        boolean securityActive,
        boolean accOff,
        boolean sosAlarm,
        boolean vibrationAlarm,
        boolean lowBatteryAlarm,
        boolean powerCutAlarm,
        boolean backupBattery,
        boolean antiTamperAlarm,
        boolean oilCutOff,
        boolean batteryRemoved,
        boolean doorOpen,
        boolean overspeedAlarm,
        boolean fenceInAlarm,
        boolean fenceOutAlarm
) {

    public static DeviceStatus allClear() {
        return new DeviceStatus(false, false, false, false, false, false,
                false, false, false, false, false, false, false, false, false);
    }

    public boolean isAlarmActive() {
        return sosAlarm || vibrationAlarm || lowBatteryAlarm || powerCutAlarm ||
                antiTamperAlarm || overspeedAlarm || fenceInAlarm || fenceOutAlarm;
    }
}
