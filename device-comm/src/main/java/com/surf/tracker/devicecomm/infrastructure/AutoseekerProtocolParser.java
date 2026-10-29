package com.surf.tracker.devicecomm.infrastructure;

import com.surf.tracker.devicecomm.domain.ITrackerProtocolParser;
import com.surf.tracker.devicecomm.exception.ProtocolException;
import com.surf.tracker.devicecomm.exception.ProtocolParseException;
import com.surf.tracker.shared.domain.LocationPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoField;
import java.util.HashMap;
import java.util.Map;
import java.util.Locale;
import java.util.regex.Pattern;

public class AutoseekerProtocolParser implements ITrackerProtocolParser {
    private static final Pattern AUTOSEEKER_PATTERN = Pattern.compile(
            "^\\*[A-Z]{2},[0-9]{10},(V[0-5]|R12|D1|S20|SCF|S71|R7|LOCK|MILE),.*#"
    );

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            new DateTimeFormatterBuilder().appendPattern("ddMM")
                .appendValueReduced(ChronoField.YEAR, 2, 2, 2000).appendPattern("HHmmss")
                .toFormatter(Locale.ROOT).withResolverStyle(ResolverStyle.STRICT);

    private final ZoneId timestampZone;
    private final int maxMessageLength;

    public AutoseekerProtocolParser() {
        this(ZoneOffset.UTC, 1024);
    }

    public AutoseekerProtocolParser(ZoneId timestampZone, int maxMessageLength) {
        this.timestampZone = java.util.Objects.requireNonNull(timestampZone);
        if (maxMessageLength < 1 || maxMessageLength > 65536) {
            throw new IllegalArgumentException("Autoseeker maximum message length must be between 1 and 65536");
        }
        this.maxMessageLength = maxMessageLength;
    }

    private final Logger log = LoggerFactory.getLogger(AutoseekerProtocolParser.class);
    private volatile String lastParsedType;

    @Override
    public LocationPoint parse(String data) throws ProtocolParseException {
        try {
            if (!canParse(data)) {
                throw new ProtocolParseException("Not a valid Autoseeker protocol message");
            }

            // Remove * and #
            String cleanData = data.substring(1, data.length() - 1);
            String[] parts = cleanData.split(",", -1);

            String protocolType = parts[2]; // V1, V4, etc.
            this.lastParsedType = protocolType;

            return switch (protocolType) {
                case "V1" -> parseHeartPackPacket(parts);
                case "V4" -> parseCommandResponsePacket(parts);
                default -> throw new ProtocolParseException("Unknown protocol type: " + protocolType);
            };

        } catch (Exception e) {
            throw new ProtocolParseException("Failed to parse Autoseeker data: " + e.getMessage());
        }
    }

    private LocationPoint parseHeartPackPacket(String[] parts) throws ProtocolParseException {
        try {
            // Format: *HQ,8168000008,V1,043602,A,2234.9273,N,11354.3980,E,000.06,000,100715,FBFBBFF,460,00,10342,4283,10,25,128#

            if (parts.length < 17) {
                throw new ProtocolParseException("Incomplete heart pack packet");
            }

            return parseGpsFix(parts, 4, parts[3], false);

        } catch (Exception e) {
            throw new ProtocolParseException("Failed to parse heart pack packet: " + e.getMessage());
        }
    }

    private LocationPoint parseCommandResponsePacket(String[] parts) throws ProtocolParseException {
        try {
            // Format varies by command, e.g.:
            // *HQ,8168000005,V4,D1,30,65535,062108,062025,A,2235.0086,N,11354.3668,E,000.00,000,160716,FFFFBBFF,460,00,10342,3721#
            // *HQ,8168000005,V4,S20,DONE,061158,061116,A,2235.0086,N,11354.3668,E,000.00,000,160716F7FFBBFF,460,00,10342,3721#

            if (parts.length < 15) {
                throw new ProtocolParseException("Incomplete command response packet");
            }

            String commandCode = parts[3];
            // Match the D1/S20/SCF layouts documented above and emitted by our simulator.
            int gpsDataStartIndex = "D1".equals(commandCode) ? 8 : "S20".equals(commandCode) ? 7 : 6;
            return parseGpsFix(parts, gpsDataStartIndex, parts[gpsDataStartIndex - 1], "S20".equals(commandCode));

        } catch (Exception e) {
            throw new ProtocolParseException("Failed to parse command response packet: " + e.getMessage());
        }
    }

    private LocationPoint parseGpsFix(String[] parts, int start, String time, boolean combinedDateStatus) {
        if (parts.length <= start + 7 || !"A".equals(parts[start])) {
            throw new IllegalArgumentException("Packet does not contain a valid GPS fix");
        }
        double latitude = parseCoordinate(parts[start + 1], parts[start + 2], true);
        double longitude = parseCoordinate(parts[start + 3], parts[start + 4], false);
        String speedText = parts[start + 5];
        if (!speedText.matches("[0-9]+(?:\\.[0-9]+)?")) throw new IllegalArgumentException("Invalid speed");
        float speed = Float.parseFloat(speedText) * 1.852f;
        if (!Float.isFinite(speed)) throw new IllegalArgumentException("Speed is not finite");
        String headingText = parts[start + 6];
        if (!headingText.matches("[0-9]+(?:\\.[0-9]+)?")) throw new IllegalArgumentException("Invalid heading");
        double heading = Double.parseDouble(headingText);
        if (!Double.isFinite(heading) || heading >= 360) throw new IllegalArgumentException("Heading outside [0,360)");
        String date = parts[start + 7];
        if (combinedDateStatus && date.matches("[0-9]{6}[0-9A-Fa-f]{1,8}")) date = date.substring(0, 6);
        return new LocationPoint(latitude, longitude, speed, parseDateTime(date, time));
    }

    private double parseCoordinate(String text, String direction, boolean latitude) {
        int degreesLength = latitude ? 2 : 3;
        String normalized = text;
        if (!text.contains(".") && text.length() > degreesLength + 2 && text.matches("[0-9]+")) {
            normalized = text.substring(0, degreesLength + 2) + "." + text.substring(degreesLength + 2);
        }
        if (!normalized.matches("[0-9]{" + (degreesLength + 2) + "}(?:\\.[0-9]+)?")) {
            throw new IllegalArgumentException("Invalid coordinate representation");
        }
        boolean negative = latitude ? "S".equals(direction) : "W".equals(direction);
        boolean positive = latitude ? "N".equals(direction) : "E".equals(direction);
        if (!negative && !positive) throw new IllegalArgumentException("Invalid coordinate hemisphere");
        int degrees = Integer.parseInt(normalized.substring(0, degreesLength));
        double minutes = Double.parseDouble(normalized.substring(degreesLength));
        int maximum = latitude ? 90 : 180;
        if (!Double.isFinite(minutes) || minutes >= 60 || degrees > maximum || (degrees == maximum && minutes > 0)) {
            throw new IllegalArgumentException("Coordinate outside valid range");
        }
        double value = degrees + minutes / 60.0;
        return negative ? -value : value;
    }

    private Instant parseDateTime(String dateStr, String timeStr) {
        if (!dateStr.matches("[0-9]{6}") || !timeStr.matches("[0-9]{6}")) {
            throw new IllegalArgumentException("Invalid GPS date or time");
        }
        LocalDateTime localTime = LocalDateTime.parse(dateStr + timeStr, DATE_TIME_FORMATTER);
        var validOffsets = timestampZone.getRules().getValidOffsets(localTime);
        if (validOffsets.size() != 1) {
            throw new IllegalArgumentException("GPS time is nonexistent or ambiguous in configured timezone");
        }
        return localTime.toInstant(validOffsets.getFirst());
    }

    @Override
    public boolean canParse(String data) {
        if (data == null || data.isEmpty() || data.length() > maxMessageLength) {
            return false;
        }
        for (int index = 0; index < data.length(); index++) {
            char c = data.charAt(index);
            if (c < 32 || c > 126 || (c == '#' && index != data.length() - 1)) return false;
        }
        return AUTOSEEKER_PATTERN.matcher(data).matches();
    }

    @Override
    public String buildFuelCutCommand(String deviceId) {
        try {
            // Autoseeker fuel cut command format: *HQ,IMEI,S20,HHMMSS,1,3,10,3,5,5,3,5,3,5,3,5#
            String timeStr = getCurrentTimeString();
            // Pattern: 1 (static disable), then timing pattern
            String command = String.format("*HQ,%s,S20,%s,1,3,10,3,5,5,3,5,3,5,3,5#", deviceId, timeStr);
            return command;

        } catch (Exception e) {
            throw ProtocolException.commandBuildError("Autoseeker", "fuel cut");
        }
    }

    @Override
    public String buildEngineOnCommand(String deviceId) {
        try {
            // Autoseeker fuel restore command: *HQ,IMEI,S20,HHMMSS,0,0#
            String timeStr = getCurrentTimeString();
            String command = String.format("*HQ,%s,S20,%s,0,0#", deviceId, timeStr);
            return command;

        } catch (Exception e) {
            throw ProtocolException.commandBuildError("Autoseeker", "engine on");
        }
    }

    @Override
    public String getProtocolName() {
        return "Autoseeker";
    }

    @Override
    public PacketType getPacketType() {
        if (lastParsedType == null) {
            return PacketType.UNKNOWN;
        }
        return switch (lastParsedType) {
            case "V1" -> PacketType.GPS;
            case "V4" -> PacketType.COMMAND_RESPONSE;
            default -> PacketType.UNKNOWN;
        };
    }

    // Additional utility methods
    public ExtendedDeviceData parseExtendedData(String[] parts) {
        try {
            String vehicleStatus = parts.length > 12 ? parts[12] : null;
            String mcc = parts.length > 13 ? parts[13] : null;
            String mnc = parts.length > 14 ? parts[14] : null;
            String lac = parts.length > 15 ? parts[15] : null;
            String cellId = parts.length > 16 ? parts[16] : null;

            Integer gpsSignal = null;
            Integer gsmSignal = null;
            Integer voltage = null;
            if (parts.length > 17) {
                try {
                    gpsSignal = Integer.parseInt(parts[17]);
                    gsmSignal = Integer.parseInt(parts[18]);
                    voltage = Integer.parseInt(parts[19]);
                } catch (NumberFormatException e) {
                    // Ignore if can't parse
                }
            }

            return new ExtendedDeviceData(vehicleStatus, mcc, mnc, lac, cellId, gpsSignal, gsmSignal, voltage);
        } catch (Exception e) {
            log.warn("Error parsing extended data: {}", e.getMessage());
            return null;
        }
    }

    public Map<String, Boolean> parseVehicleStatus(String statusHex) {
        Map<String, Boolean> statusMap = new HashMap<>();

        try {
            if (statusHex == null || statusHex.length() < 8) {
                return statusMap;
            }

            long value = Long.parseLong(statusHex, 16);
            String binary = String.format("%32s", Long.toBinaryString(value)).replace(' ', '0');

            // Autoseeker status bits per Appendix 1 - LSB first, 0 = active.
            statusMap.put("saveStatus",          isActive(binary, 0));
            statusMap.put("removeAlarm",         isActive(binary, 1));
            statusMap.put("supplementaryData",   isActive(binary, 2));
            statusMap.put("cutFuelElectricity",  isActive(binary, 3));
            statusMap.put("batteryRemoveAlarm",  isActive(binary, 4));
            statusMap.put("shakeAlarm",          isActive(binary, 8));
            statusMap.put("setFence",            isActive(binary, 9));
            statusMap.put("accClose",            isActive(binary, 10));
            statusMap.put("useBackupBattery",    isActive(binary, 16));
            statusMap.put("openLock",            isActive(binary, 17));
            statusMap.put("engineStatus",        isActive(binary, 24));

        } catch (Exception e) {
            log.warn("Error parsing vehicle status: {}", e.getMessage());
        }

        return statusMap;
    }

    private boolean isActive(String binary, int lsbIndex) {
        if (lsbIndex < 0 || lsbIndex >= binary.length()) {
            return false;
        }
        return binary.charAt(binary.length() - 1 - lsbIndex) == '0';
    }

    private String getCurrentTimeString() {
        return String.format("%02d%02d%02d",
                java.time.LocalTime.now().getHour(),
                java.time.LocalTime.now().getMinute(),
                java.time.LocalTime.now().getSecond());
    }

}
