package com.surf.tracker.devicecomm.application;

import com.surf.tracker.shared.pubsub.DeviceCommandBuilder;
import com.surf.tracker.devicecomm.infrastructure.AutoseekerProtocolParser;
import org.springframework.stereotype.Component;

@Component
public class AutoseekerCommandBuilder implements DeviceCommandBuilder {
    private final AutoseekerProtocolParser parser;

    public AutoseekerCommandBuilder(AutoseekerProtocolParser parser) {
        this.parser = parser;
    }

    @Override
    public boolean supports(String protocol) {
        return "AUTOSEEKER".equalsIgnoreCase(protocol);
    }

    @Override
    public String buildFuelCutCommand(String deviceId) {
        return parser.buildFuelCutCommand(deviceId);
    }

    @Override
    public String buildEngineOnCommand(String deviceId) {
        return parser.buildEngineOnCommand(deviceId);
    }
}
