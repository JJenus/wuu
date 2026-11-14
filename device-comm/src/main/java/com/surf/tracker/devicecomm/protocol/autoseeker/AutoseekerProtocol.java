package com.surf.tracker.devicecomm.protocol.autoseeker;

import com.surf.tracker.devicecomm.exception.ProtocolException;
import com.surf.tracker.devicecomm.protocol.CommandCodec;
import com.surf.tracker.devicecomm.protocol.DeviceCommand;
import com.surf.tracker.devicecomm.protocol.DeviceProtocol;
import com.surf.tracker.devicecomm.protocol.Frame;
import com.surf.tracker.devicecomm.protocol.FramingSpec;
import com.surf.tracker.devicecomm.protocol.common.StatusBitMap;
import com.surf.tracker.devicecomm.protocol.hq.HqCommandCodec;
import com.surf.tracker.devicecomm.protocol.hq.HqFixException;
import com.surf.tracker.devicecomm.protocol.hq.HqFrame;
import com.surf.tracker.devicecomm.protocol.hq.HqGpsFix;
import com.surf.tracker.shared.domain.DeviceAlarm;
import com.surf.tracker.shared.domain.DeviceHealth;
import com.surf.tracker.devicecomm.protocol.model.DeviceMessage;
import com.surf.tracker.devicecomm.protocol.model.PositionReport;
import com.surf.tracker.shared.domain.LocationPoint;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

import static com.surf.tracker.devicecomm.protocol.common.StatusBitMap.bit;

/** Autoseeker trackers: {@code *HQ,<10-digit id>,<type>,...#}. Stateless and thread-safe. */
public final class AutoseekerProtocol implements DeviceProtocol {
    public static final String NAME = "Autoseeker";
    private static final Pattern FRAME = Pattern.compile(
            "^\\*[A-Z]{2},[0-9]{10},(V[0-5]|R12|D1|S20|SCF|S71|R7|LOCK|MILE),.*#");

    /** Autoseeker protocol Appendix 1 (LSB first, 0 = active). Unmapped bits are state, not alarms. */
    private static final StatusBitMap STATUS = StatusBitMap.activeLow(
            bit(1, DeviceAlarm.TAMPER), bit(3, DeviceAlarm.OIL_CUT),
            bit(4, DeviceAlarm.BATTERY_REMOVED), bit(8, DeviceAlarm.VIBRATION));

    private static final CommandCodec COMMANDS = new HqCommandCodec(NAME, Map.of(
            DeviceCommand.FUEL_CUT, "*HQ,%s,S20,%s,1,3,10,3,5,5,3,5,3,5#",
            DeviceCommand.ENGINE_RESTORE, "*HQ,%s,S20,%s,0,0#"));

    private final ZoneId timestampZone;
    private final int maxMessageLength;

    public AutoseekerProtocol() {
        this(ZoneOffset.UTC, 1024);
    }

    public AutoseekerProtocol(ZoneId timestampZone, int maxMessageLength) {
        this.timestampZone = Objects.requireNonNull(timestampZone);
        if (maxMessageLength < 1 || maxMessageLength > 65536)
            throw new IllegalArgumentException("Autoseeker maximum message length must be between 1 and 65536");
        this.maxMessageLength = maxMessageLength;
    }

    @Override
    public String name() {
        return NAME;
    }

    /** Strict 10-digit ids are claimed before the looser GT06 family. */
    @Override
    public int detectionPriority() {
        return 10;
    }

    @Override
    public FramingSpec framing() {
        return new FramingSpec.Delimited((byte) '#', maxMessageLength);
    }

    @Override
    public boolean recognizes(Frame frame) {
        String text = HqFrame.text(frame, maxMessageLength);
        return text != null && FRAME.matcher(text).matches();
    }

    @Override
    public List<DeviceMessage> decode(Frame frame, Instant receivedAt) {
        String text = HqFrame.text(frame, maxMessageLength);
        if (text == null || !FRAME.matcher(text).matches())
            throw ProtocolException.parseError(NAME, "not an Autoseeker frame");
        String[] parts = HqFrame.split(text);
        String deviceId = parts[1];
        try {
            return switch (parts[2]) {
                case "V1" -> List.of(position(deviceId, receivedAt, parts));
                case "V4" -> List.of(commandResponse(deviceId, receivedAt, parts));
                default -> throw ProtocolException.parseError(NAME, "unsupported packet type");
            };
        } catch (HqFixException fix) {
            throw ProtocolException.parseError(NAME, fix.getMessage());
        }
    }

    /** {@code *HQ,id,V1,time,A,lat,N,lon,E,speed,heading,date,status,mcc,mnc,lac,cell,gps,gsm,volt#} */
    private PositionReport position(String deviceId, Instant receivedAt, String[] parts) {
        if (parts.length < 17) throw ProtocolException.parseError(NAME, "incomplete position packet");
        LocationPoint point = HqGpsFix.parse(parts, 4, parts[3], false, timestampZone);
        DeviceHealth health = new DeviceHealth(null, null, HqFrame.intField(parts, 18),
                HqFrame.intField(parts, 17), STATUS.decode(parts[12]));
        return new PositionReport(deviceId, receivedAt, point, health);
    }

    /** D1, S20 and SCF responses embed a GPS fix at command-specific offsets. */
    private PositionReport commandResponse(String deviceId, Instant receivedAt, String[] parts) {
        if (parts.length < 15) throw ProtocolException.parseError(NAME, "incomplete command response");
        String command = parts[3];
        int start = "D1".equals(command) ? 8 : "S20".equals(command) ? 7 : 6;
        LocationPoint point = HqGpsFix.parse(parts, start, parts[start - 1], "S20".equals(command), timestampZone);
        return new PositionReport(deviceId, receivedAt, point, DeviceHealth.NONE);
    }

    @Override
    public CommandCodec commands() {
        return COMMANDS;
    }
}
