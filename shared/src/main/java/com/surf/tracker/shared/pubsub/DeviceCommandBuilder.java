package com.surf.tracker.shared.pubsub;

public interface DeviceCommandBuilder {
    boolean supports(String protocol);
    String buildFuelCutCommand(String deviceId);
    String buildEngineOnCommand(String deviceId);
}
