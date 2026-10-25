package com.surf.tracker.devicecomm.infrastructure;

public record ExtendedDeviceData(
        String vehicleStatus,
        String mcc,
        String mnc,
        String lac,
        String cellId,
        Integer gpsSignal,
        Integer gsmSignal,
        Integer voltage
) {
}
