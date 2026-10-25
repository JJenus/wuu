package com.surf.tracker.core.api.dto;

import com.surf.tracker.core.domain.enums.TrackerStatus;

public class UpdateTrackerRequest {
    private String model;
    private String protocol;
    private String firmwareVersion;
    private String simNumber;
    private Float batteryLevel;
    private Integer signalStrength;
    private TrackerStatus status;

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public String getProtocol() { return protocol; }
    public void setProtocol(String protocol) { this.protocol = protocol; }

    public String getFirmwareVersion() { return firmwareVersion; }
    public void setFirmwareVersion(String firmwareVersion) { this.firmwareVersion = firmwareVersion; }

    public String getSimNumber() { return simNumber; }
    public void setSimNumber(String simNumber) { this.simNumber = simNumber; }

    public Float getBatteryLevel() { return batteryLevel; }
    public void setBatteryLevel(Float batteryLevel) { this.batteryLevel = batteryLevel; }

    public Integer getSignalStrength() { return signalStrength; }
    public void setSignalStrength(Integer signalStrength) { this.signalStrength = signalStrength; }

    public TrackerStatus getStatus() { return status; }
    public void setStatus(TrackerStatus status) { this.status = status; }
}
