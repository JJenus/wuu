package com.surf.tracker.devicecomm.domain;

import com.surf.tracker.shared.domain.LocationPoint;
import com.surf.tracker.devicecomm.exception.ProtocolParseException;

public interface ITrackerProtocolParser {
    LocationPoint parse(String rawData) throws ProtocolParseException;
    boolean canParse(String rawData);
    String buildFuelCutCommand(String deviceId);
    String buildEngineOnCommand(String deviceId);
    String getProtocolName();
    PacketType getPacketType();
    enum PacketType {
        GPS,
        LOGIN,
        HEARTBEAT,
        COMMAND_RESPONSE,
        LBS,
        WIFI,
        UNKNOWN
    }
}
