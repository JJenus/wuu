package com.surf.tracker.devicecomm.protocol.gt06;

import com.surf.tracker.devicecomm.exception.ProtocolException;
import com.surf.tracker.devicecomm.protocol.CommandCodec;
import com.surf.tracker.devicecomm.protocol.DeviceCommand;
import com.surf.tracker.devicecomm.protocol.DeviceProtocol;
import com.surf.tracker.devicecomm.protocol.Frame;
import com.surf.tracker.devicecomm.protocol.FramingSpec;
import com.surf.tracker.devicecomm.protocol.common.BatteryCurve;
import com.surf.tracker.devicecomm.protocol.common.StatusBitMap;
import com.surf.tracker.devicecomm.protocol.hq.HqCommandCodec;
import com.surf.tracker.devicecomm.protocol.hq.HqFixException;
import com.surf.tracker.devicecomm.protocol.hq.HqFrame;
import com.surf.tracker.devicecomm.protocol.hq.HqGpsFix;
import com.surf.tracker.shared.domain.DeviceAlarm;
import com.surf.tracker.shared.domain.DeviceHealth;
import com.surf.tracker.devicecomm.protocol.model.DeviceMessage;
import com.surf.tracker.devicecomm.protocol.model.LoginReport;
import com.surf.tracker.devicecomm.protocol.model.PositionReport;
import com.surf.tracker.devicecomm.protocol.model.StatusReport;
import com.surf.tracker.shared.domain.LocationPoint;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

import static com.surf.tracker.devicecomm.protocol.common.StatusBitMap.bit;

/** GT06-family trackers: {@code *XX,<10-20 digit id>,<type>,...#}. Stateless and thread-safe. */
public final class Gt06Protocol implements DeviceProtocol {
    public static final String NAME = "GT06";
    private static final Pattern FRAME = Pattern.compile(
            "^\\*[A-Z]{2},[0-9]{10,20},(V[0-5]|HTBT|S20|D[0-9]+)(,.*)?#");

    /** GT06 status word: LSB first, 0 = active. Unmapped bits (ACC, GPS, engine) are state, not alarms. */
    private static final StatusBitMap STATUS = StatusBitMap.activeLow(
            bit(0, DeviceAlarm.SOS), bit(1, DeviceAlarm.OVERSPEED), bit(2, DeviceAlarm.ROUTE_DEVIATION),
            bit(3, DeviceAlarm.VIBRATION), bit(4, DeviceAlarm.LOW_BATTERY), bit(5, DeviceAlarm.POWER_CUT),
            bit(6, DeviceAlarm.FENCE_IN), bit(7, DeviceAlarm.FENCE_OUT), bit(11, DeviceAlarm.DOOR_OPEN),
            bit(12, DeviceAlarm.OIL_CUT), bit(13, DeviceAlarm.BATTERY_REMOVED), bit(14, DeviceAlarm.TAMPER));

    private static final BatteryCurve BATTERY = new BatteryCurve(1024f, 5.6f, 3.6f);

    private static final CommandCodec COMMANDS = new HqCommandCodec(NAME, Map.of(
            DeviceCommand.FUEL_CUT, "*HQ,%s,S20,%s,1,3#",
            DeviceCommand.ENGINE_RESTORE, "*HQ,%s,S20,%s,1,0#"));

    private final ZoneId timestampZone;
    private final int maxMessageLength;

    public Gt06Protocol() {
        this(ZoneOffset.UTC, 1024);
    }

    public Gt06Protocol(ZoneId timestampZone, int maxMessageLength) {
        this.timestampZone = Objects.requireNonNull(timestampZone);
        if (maxMessageLength < 1 || maxMessageLength > 65536)
            throw new IllegalArgumentException("GT06 maximum message length must be between 1 and 65536");
        this.maxMessageLength = maxMessageLength;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public int detectionPriority() {
        return 20;
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
            throw ProtocolException.parseError(NAME, "not a GT06 frame");
        String[] parts = HqFrame.split(text);
        String deviceId = parts[1];
        try {
            DeviceMessage message = switch (parts[2]) {
                case "V0" -> new LoginReport(deviceId, receivedAt);
                case "V1", "V2" -> position(deviceId, receivedAt, parts, 4, false);
                case "V4" -> parts.length > 3 && "S20".equals(parts[3])
                        ? commandResponse(deviceId, receivedAt, parts)
                        : position(deviceId, receivedAt, parts, 4, false);
                case "V3" -> lbs(deviceId, receivedAt, parts);
                case "V5" -> wifi(deviceId, receivedAt, parts);
                case "HTBT" -> heartbeat(deviceId, receivedAt, parts);
                default -> throw ProtocolException.parseError(NAME, "unsupported packet type");
            };
            return List.of(message);
        } catch (HqFixException fix) {
            throw ProtocolException.parseError(NAME, fix.getMessage());
        }
    }

    /** {@code *XX,id,V1,HHMMSS,A,lat,N,lon,E,speed,heading,DDMMYY,status#} */
    private PositionReport position(String deviceId, Instant receivedAt, String[] parts, int start, boolean combined) {
        if (parts.length < start + 8) throw ProtocolException.parseError(NAME, "incomplete position packet");
        LocationPoint point = HqGpsFix.parse(parts, start, parts[start - 1], combined, timestampZone);
        String status = parts.length > 12 ? parts[12] : null;
        return new PositionReport(deviceId, receivedAt, point, DeviceHealth.ofAlarms(STATUS.decode(status)));
    }

    /** {@code *HQ,id,V4,S20,DONE,respTime,gpsTime,A,lat,N,lon,E,speed,heading,DDMMYY<status>,...#} */
    private PositionReport commandResponse(String deviceId, Instant receivedAt, String[] parts) {
        if (parts.length < 15) throw ProtocolException.parseError(NAME, "incomplete command response");
        LocationPoint point = HqGpsFix.parse(parts, 7, parts[6], true, timestampZone);
        return new PositionReport(deviceId, receivedAt, point, DeviceHealth.NONE);
    }

    /**
     * Cell-tower frame without a fix. The battery field position is not fixed in the frame, so the
     * first 4-hex-digit field after the cell info is taken; treat the reading as best-effort.
     */
    private StatusReport lbs(String deviceId, Instant receivedAt, String[] parts) {
        if (parts.length < 9) throw ProtocolException.parseError(NAME, "incomplete LBS packet");
        DeviceHealth health = DeviceHealth.NONE;
        for (int i = 5; i < parts.length; i++) {
            var reading = BATTERY.fromHex(parts[i]);
            if (reading.isPresent()) {
                health = DeviceHealth.ofBattery(reading.get().percent(), reading.get().volts());
                break;
            }
        }
        return new StatusReport(deviceId, receivedAt, health);
    }

    private StatusReport wifi(String deviceId, Instant receivedAt, String[] parts) {
        if (parts.length < 8) throw ProtocolException.parseError(NAME, "incomplete WIFI packet");
        return new StatusReport(deviceId, receivedAt, DeviceHealth.NONE);
    }

    /** {@code *XX,id,HTBT#} or {@code *XX,id,HTBT,battery_percent#} */
    private StatusReport heartbeat(String deviceId, Instant receivedAt, String[] parts) {
        Integer percent = HqFrame.intField(parts, 3);
        DeviceHealth health = percent != null && percent <= 100 ? DeviceHealth.ofBattery(percent, null) : DeviceHealth.NONE;
        return new StatusReport(deviceId, receivedAt, health);
    }

    @Override
    public CommandCodec commands() {
        return COMMANDS;
    }
}
