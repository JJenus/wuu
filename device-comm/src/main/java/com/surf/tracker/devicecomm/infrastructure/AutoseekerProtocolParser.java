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
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class AutoseekerProtocolParser implements ITrackerProtocolParser {
    private static final Pattern AUTOSEEKER_PATTERN = Pattern.compile(
            "^\\*[A-Z]{2},[0-9]{10},(V[0-5]|R12|D1|S20|SCF|S71|R7|LOCK|MILE),.*#"
    );

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("ddMMyyHHmmss");

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
            String[] parts = cleanData.split(",");

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

            String deviceId = parts[1];
            String timeStr = parts[3];
            String validity = parts[4];
            String latStr = parts[5];
            String latDir = parts[6];
            String lonStr = parts[7];
            String lonDir = parts[8];
            String speedStr = parts[9];
            String directionStr = parts[10];
            String dateStr = parts[11];
            String vehicleStatus = parts[12];

            // Parse latitude (DDMM.MMMM format)
            double latitude = parseDDMMtoDecimal(latStr);
            if ("S".equals(latDir)) {
                latitude = -latitude;
            }

            // Parse longitude (DDDMM.MMMM format)
            double longitude = parseDDMMtoDecimal(lonStr);
            if ("W".equals(lonDir)) {
                longitude = -longitude;
            }

            // Parse speed (knots to km/h)
            float speedKnots = Float.parseFloat(speedStr);
            float speedKmh = speedKnots * 1.852f;

            // Parse timestamp
            Instant timestamp = parseDateTime(dateStr, timeStr);

            // Data is only valid if validity is 'A'
            boolean isValid = "A".equals(validity);
            if (!isValid) {
                speedKmh = 0.0f;
                log.debug("GPS data not valid (validity={})", validity);
            }

            // Parse additional network information if available
            if (parts.length > 13) {
                String mcc = parts[13];
                String mnc = parts[14];
                String lac = parts[15];
                String cellId = parts[16];

                log.debug("Network info: MCC={}, MNC={}, LAC={}, CellID={}",
                        mcc, mnc, lac, cellId);

                // Parse GPS/GSM signal and voltage if available
                if (parts.length > 19) {
                    int gpsSignal = Integer.parseInt(parts[17]);
                    int gsmSignal = Integer.parseInt(parts[18]);
                    int voltage = Integer.parseInt(parts[19]);

                    log.debug("Signal info: GPS={}, GSM={}, Voltage={}",
                            gpsSignal, gsmSignal, voltage);
                }
            }

            log.debug("Parsed Autoseeker location: lat={}, lon={}, speed={} km/h, time={}, status={}",
                    latitude, longitude, speedKmh, timestamp, vehicleStatus);

            return new LocationPoint(latitude, longitude, speedKmh, timestamp);

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

            String deviceId = parts[1];
            String commandCode = parts[3];
            String status = parts[4];

            int gpsDataStartIndex;
            String responseTime;
            String gpsTime;

            // Different response formats for different commands
            if ("D1".equals(commandCode)) {
                // D1 format: V4,D1,30,65535,062108,062025,A,...
                responseTime = parts[5];  // 062108
                gpsTime = parts[6];       // 062025
                gpsDataStartIndex = 7;
            } else if ("S20".equals(commandCode)) {
                // S20 format: V4,S20,DONE,061158,061116,A,...
                responseTime = parts[4];  // DONE (but actually it's status)
                gpsTime = parts[5];       // 061158
                gpsDataStartIndex = 6;
            } else {
                // Default format for other commands
                responseTime = parts[4];
                gpsTime = parts[5];
                gpsDataStartIndex = 6;
            }

            // Parse GPS data if available
            if (parts.length > gpsDataStartIndex + 9) {
                String validity = parts[gpsDataStartIndex];
                String latStr = parts[gpsDataStartIndex + 1];
                String latDir = parts[gpsDataStartIndex + 2];
                String lonStr = parts[gpsDataStartIndex + 3];
                String lonDir = parts[gpsDataStartIndex + 4];
                String speedStr = parts[gpsDataStartIndex + 5];
                String directionStr = parts[gpsDataStartIndex + 6];
                String dateStatus = parts[gpsDataStartIndex + 7]; // May contain date and status combined

                // Parse latitude
                double latitude = parseDDMMtoDecimal(latStr);
                if ("S".equals(latDir)) {
                    latitude = -latitude;
                }

                // Parse longitude
                double longitude = parseDDMMtoDecimal(lonStr);
                if ("W".equals(lonDir)) {
                    longitude = -longitude;
                }

                // Parse speed
                float speedKnots = Float.parseFloat(speedStr);
                float speedKmh = speedKnots * 1.852f;

                // Extract date from combined field (e.g., "160716F7FFBBFF")
                String dateStr;
                if (dateStatus.length() >= 6) {
                    dateStr = dateStatus.substring(0, 6);
                } else {
                    dateStr = getCurrentDateString();
                }

                // Parse timestamp
                Instant timestamp = parseDateTime(dateStr, gpsTime);

                log.debug("Command response parsed: cmd={}, status={}, lat={}, lon={}, speed={} km/h",
                        commandCode, status, latitude, longitude, speedKmh);

                return new LocationPoint(latitude, longitude, speedKmh, timestamp);
            } else {
                // No GPS data in response, use current time
                log.debug("Command response without GPS data: cmd={}, status={}", commandCode, status);
                return new LocationPoint(0.0, 0.0, 0.0f, Instant.now());
            }

        } catch (Exception e) {
            throw new ProtocolParseException("Failed to parse command response packet: " + e.getMessage());
        }
    }

    private double parseDDMMtoDecimal(String ddmm) {
        try {
            // Handle formats like "2234.9273" or "22349273"
            String normalized = ddmm;
            if (ddmm.length() == 8 && !ddmm.contains(".")) {
                // Format: DDMMmmm (e.g., 22349273)
                // Insert decimal point at appropriate position
                normalized = ddmm.substring(0, 4) + "." + ddmm.substring(4);
            }

            double value = Double.parseDouble(normalized);
            int degrees = (int)(value / 100);
            double minutes = value - (degrees * 100);
            return degrees + (minutes / 60.0);
        } catch (NumberFormatException e) {
            log.warn("Failed to parse DDMM coordinate: {}", ddmm);
            return 0.0;
        }
    }

    private Instant parseDateTime(String dateStr, String timeStr) {
        try {
            String dateTimeStr = dateStr + timeStr;
            LocalDateTime ldt = LocalDateTime.parse(dateTimeStr, DATE_TIME_FORMATTER);
            return ldt.atZone(ZoneId.systemDefault()).toInstant();
        } catch (Exception e) {
            log.warn("Failed to parse timestamp, using current time: {}", e.getMessage());
            return Instant.now();
        }
    }

    @Override
    public boolean canParse(String data) {
        if (data == null || data.isEmpty()) {
            return false;
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

    private String getCurrentDateString() {
        return String.format("%02d%02d%02d",
                java.time.LocalDate.now().getDayOfMonth(),
                java.time.LocalDate.now().getMonthValue(),
                java.time.LocalDate.now().getYear() % 100);
    }
}