
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

public class GT06ProtocolParser implements ITrackerProtocolParser {
    private static final Pattern GT06_PATTERN = Pattern.compile("^\\*[A-Z]{2},[0-9]{10,20},(V[0-5]|HTBT|S20|D[0-9]+),.*#");
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
        DateTimeFormatter.ofPattern("ddMMyyHHmmss");
    private final Logger log = LoggerFactory.getLogger(GT06ProtocolParser.class);
    private volatile String lastParsedType;
    
    @Override
    public LocationPoint parse(String data) throws ProtocolParseException {
        try {
            if (!canParse(data)) {
                throw new ProtocolParseException("Not a valid GT06 protocol message");
            }
            
            // Remove * and #
            String cleanData = data.substring(1, data.length() - 1);
            String[] parts = cleanData.split(",");
            
            String protocolType = parts[2]; // V1, V2, V3, V4, V5, HTBT
            this.lastParsedType = protocolType;
            // Handle command response packets
            if ("V4".equals(protocolType) && parts.length >= 4 && "S20".equals(parts[3])) {
                return parseCommandResponsePacket(parts);
            }

            return switch (protocolType) {
                case "V0" -> parseLoginPacket(parts);
                case "V1", "V2", "V4" -> parseGPSPacket(parts);
                case "V3" -> parseLBSPacket(parts);
                case "V5" -> parseWifiPacket(parts);
                case "HTBT" -> parseHeartbeatPacket(parts);
                default -> throw new ProtocolParseException("Unknown protocol type: " + protocolType);
            };
            
        } catch (Exception e) {
            throw new ProtocolParseException("Failed to parse GT06 data: " + e.getMessage());
        }
    }

    private LocationPoint parseCommandResponsePacket(String[] parts) throws ProtocolParseException {
        try {
            // Format for S20 command response:
            // *HQ,IMEI,V4,S20,DONE,HHMMSS,response_time,A,latitude,N,longitude,E,speed,direction,DDMMYY,status,...
            // Example: *HQ,8168000005,V4,S20,DONE,061158,061116,A,2235.0086,N,11354.3668,E,000.00,000,160716F7FFBBFF,460,00

            log.debug("Parsing command response packet with {} parts", parts.length);

            if (parts.length < 17) {
                throw new ProtocolParseException("Incomplete command response packet");
            }

            String imei = parts[1];
            String responseTimeStr = parts[5]; // 061158 - This is the response time HHMMSS
            String gpsTimeStr = parts[6];      // 061116 - This is the GPS time
            String validity = parts[7];        // A = valid
            String latStr = parts[8];          // 2235.0086
            String latDir = parts[9];          // N
            String lonStr = parts[10];         // 11354.3668
            String lonDir = parts[11];         // E
            String speedStr = parts[12];       // 000.00
            String directionStr = parts[13];   // 000
            String dateStr = parts[14];        // 160716F7FFBBFF (this looks wrong - should be DDMMYY)

            log.debug("Parsing coordinates: lat={}{}, lon={}{}", latStr, latDir, lonStr, lonDir);

            // Parse latitude (DDMM.MMMMM format)
            double latitude = parseDDMMtoDecimal(latStr);
            if ("S".equals(latDir)) {
                latitude = -latitude;
            }

            // Parse longitude (DDDMM.MMMMM format)
            double longitude = parseDDMMtoDecimal(lonStr);
            if ("W".equals(lonDir)) {
                longitude = -longitude;
            }

            // Parse speed (knots to km/h)
            float speedKnots = 0.0f;
            try {
                speedKnots = Float.parseFloat(speedStr);
            } catch (NumberFormatException e) {
                log.warn("Failed to parse speed: {}", speedStr);
            }
            float speedKmh = speedKnots * 1.852f;

            // Parse timestamp - date might be corrupted (160716F7FFBBFF), so use response time
            // Extract just the date part if it exists
            String cleanDateStr = dateStr;
            if (dateStr.length() >= 6 && dateStr.matches(".*[0-9]{6}.*")) {
                // Try to extract DDMMYY from the beginning
                cleanDateStr = dateStr.substring(0, 6);
            } else {
                // Use current date as fallback
                cleanDateStr = getCurrentDateString();
            }

            Instant timestamp;
            try {
                timestamp = parseDateTime(cleanDateStr, gpsTimeStr);
            } catch (Exception e) {
                log.warn("Failed to parse timestamp, using response time: {}", e.getMessage());
                timestamp = parseDateTime(cleanDateStr, responseTimeStr);
            }

            // Data is only valid if validity is 'A'
            boolean isValid = "A".equals(validity);
            if (!isValid) {
                speedKmh = 0.0f;
                log.debug("GPS data not valid (validity={})", validity);
            }

            log.debug("Parsed location: lat={}, lon={}, speed={} km/h, time={}",
                    latitude, longitude, speedKmh, timestamp);

            return new LocationPoint(latitude, longitude, speedKmh, timestamp);

        } catch (Exception e) {
            throw new ProtocolParseException("Failed to parse command response packet: " + e.getMessage());
        }
    }
    
    private LocationPoint parseGPSPacket(String[] parts) throws ProtocolParseException {
        try {
            // Format: *XX,IMEI,V1/V2/V4,HHMMSS,valid,latitude,N/S,longitude,E/W,speed,direction,DDMMYY,status#
            // Example: *HQ,865205030330012,V1,145452,A,2240.55181,N,11358.32389,E,0.00,0,100815,FFFFFBFF#
            
            if (parts.length < 12) {
                throw new ProtocolParseException("Incomplete GPS packet");
            }
            
            String imei = parts[1];
            String timeStr = parts[3];
            String validity = parts[4];
            String latStr = parts[5];
            String latDir = parts[6];
            String lonStr = parts[7];
            String lonDir = parts[8];
            String speedKnotStr = parts[9];
            String directionStr = parts[10];
            String dateStr = parts[11];
            
            // Parse latitude (DDMM.MMMMM format)
            double latitude = parseDDMMtoDecimal(latStr);
            if ("S".equals(latDir)) {
                latitude = -latitude;
            }
            
            // Parse longitude (DDDMM.MMMMM format)
            double longitude = parseDDMMtoDecimal(lonStr);
            if ("W".equals(lonDir)) {
                longitude = -longitude;
            }
            
            // Parse speed (knots to km/h)
            float speedKnots = Float.parseFloat(speedKnotStr);
            float speedKmh = speedKnots * 1.852f;
            
            // Parse timestamp
            Instant timestamp = parseDateTime(dateStr, timeStr);
            
            // Data is only valid if validity is 'A'
            boolean isValid = "A".equals(validity);
            
            // For invalid GPS, we might still want to process it but with 0 speed
            if (!isValid) {
                speedKmh = 0.0f;
            }
            
            return new LocationPoint(latitude, longitude, speedKmh, timestamp);
            
        } catch (Exception e) {
            throw new ProtocolParseException("Failed to parse GPS packet: " + e.getMessage());
        }
    }
    
    private LocationPoint parseLBSPacket(String[] parts) throws ProtocolParseException {
        try {
            // Format: *XX,IMEI,V3,HHMMSS,base_info,battery_info,failure_info,cont,DDMMYY,status#
            // Example: *HQ,865205030330012,V3,000201,46000,07,...#
            
            if (parts.length < 9) {
                throw new ProtocolParseException("Incomplete LBS packet");
            }
            
            String imei = parts[1];
            String timeStr = parts[3];
            String mccMnc = parts[4];
            String dateStr = parts[parts.length - 2];
            
            // Extract battery info
            String batteryHex = "0000";
            for (int i = 5; i < parts.length; i++) {
                if (parts[i].length() == 4 && parts[i].matches("[0-9A-Fa-f]{4}")) {
                    batteryHex = parts[i];
                    break;
                }
            }
            
            // Parse timestamp (for LBS, we still need a timestamp)
            Instant timestamp = parseDateTime(dateStr, timeStr);
            
            // LBS doesn't have real GPS coordinates, so we return default with 0 speed
            // In production, you'd look up the cell tower location
            return new LocationPoint(0.0, 0.0, 0.0f, timestamp);
            
        } catch (Exception e) {
            throw new ProtocolParseException("Failed to parse LBS packet: " + e.getMessage());
        }
    }
    
    private LocationPoint parseWifiPacket(String[] parts) throws ProtocolParseException {
        try {
            // Format: *XX,IMEI,V5,HHMMSS,wifi_count,wifi_info...,battery_info,DDMMYY,status#
            
            if (parts.length < 8) {
                throw new ProtocolParseException("Incomplete WIFI packet");
            }
            
            String timeStr = parts[3];
            String dateStr = parts[parts.length - 2];
            
            // Parse timestamp
            Instant timestamp = parseDateTime(dateStr, timeStr);
            
            // WIFI tracking doesn't have GPS coordinates by default
            return new LocationPoint(0.0, 0.0, 0.0f, timestamp);
            
        } catch (Exception e) {
            throw new ProtocolParseException("Failed to parse WIFI packet: " + e.getMessage());
        }
    }
    
    private LocationPoint parseHeartbeatPacket(String[] parts) throws ProtocolParseException {
        try {
            // Format: *XX,IMEI,HTBT# or *XX,IMEI,HTBT,battery_percent#
            
            String imei = parts[1];
            Instant timestamp = Instant.now();
            
            // Heartbeat doesn't have location, return current location or default
            // In your system, you might want to fetch last known location
            return new LocationPoint(0.0, 0.0, 0.0f, timestamp);
            
        } catch (Exception e) {
            throw new ProtocolParseException("Failed to parse heartbeat packet: " + e.getMessage());
        }
    }
    
    private LocationPoint parseLoginPacket(String[] parts) throws ProtocolParseException {
        // Login packet just announces device presence
        Instant timestamp = Instant.now();
        return new LocationPoint(0.0, 0.0, 0.0f, timestamp);
    }
    
    private double parseDDMMtoDecimal(String ddmm) {
        try {
            double value = Double.parseDouble(ddmm);
            int degrees = (int)(value / 100);
            double minutes = value - (degrees * 100);
            return degrees + (minutes / 60.0);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
    
    private Instant parseDateTime(String dateStr, String timeStr) {
        try {
            String dateTimeStr = dateStr + timeStr;
            LocalDateTime ldt = LocalDateTime.parse(dateTimeStr, DATE_TIME_FORMATTER);
            return ldt.atZone(ZoneId.systemDefault()).toInstant();
        } catch (Exception e) {
            // If parsing fails, return current time
            return Instant.now();
        }
    }
    
    @Override
    public boolean canParse(String data) {
        if (data == null || data.isEmpty()) {
            return false;
        }
        
        return GT06_PATTERN.matcher(data).matches();
    }
    
    @Override
    public String buildFuelCutCommand(String deviceId) {
        try {
            // GT06 fuel cut command format: *XX,IMEI,S20,HHMMSS,C,time1,time2,...#
            // From protocol: C=1 (static disable), time1=3 (3 seconds)
            
            String timeStr = getCurrentTimeString();
            String command = String.format("*HQ,%s,S20,%s,1,3#", deviceId, timeStr);
            return command;
            
        } catch (Exception e) {
            throw ProtocolException.commandBuildError("GT06", "fuel cut");
        }
    }
    
    @Override
    public String buildEngineOnCommand(String deviceId) {
        try {
            // GT06 fuel restore command: *XX,IMEI,S20,HHMMSS,1,0#
            
            String timeStr = getCurrentTimeString();
            String command = String.format("*HQ,%s,S20,%s,1,0#", deviceId, timeStr);
            return command;
            
        } catch (Exception e) {
            throw ProtocolException.commandBuildError("GT06", "engine on");
        }
    }
    
    private String getCurrentTimeString() {
        return String.format("%02d%02d%02d", 
            java.time.LocalTime.now().getHour(),
            java.time.LocalTime.now().getMinute(),
            java.time.LocalTime.now().getSecond());
    }
    
    @Override
    public String getProtocolName() {
        return "GT06";
    }

    @Override
    public PacketType getPacketType() {
        if (lastParsedType == null) {
            return PacketType.UNKNOWN;
        }
        return switch (lastParsedType) {
            case "V1", "V2", "V4" -> PacketType.GPS;
            case "V0" -> PacketType.LOGIN;
            case "V3" -> PacketType.LBS;
            case "V5" -> PacketType.WIFI;
            case "HTBT" -> PacketType.HEARTBEAT;
            default -> PacketType.COMMAND_RESPONSE;
        };
    }
    
    // Additional utility method to parse vehicle status bits
    public Map<String, Boolean> parseStatusBits(String statusHex) {
        Map<String, Boolean> statusMap = new HashMap<>();

        try {
            if (statusHex == null || statusHex.length() != 8) {
                return statusMap;
            }

            long value = Long.parseLong(statusHex, 16);
            String binary = String.format("%32s", Long.toBinaryString(value)).replace(' ', '0');

            // Per the GT06 protocol spec, status is 32 bits LSB-first.
            // Index 0 = bit 0 (LSB), index 31 = bit 31 (MSB).
            // Bits are 0 = active, 1 = inactive (per manufacturer).
            statusMap.put("sos_alarm",                isActive(binary, 0));
            statusMap.put("overspeed_alarm",          isActive(binary, 1));
            statusMap.put("route_deviation_alarm",    isActive(binary, 2));
            statusMap.put("vibration_alarm",          isActive(binary, 3));
            statusMap.put("low_battery_alarm",        isActive(binary, 4));
            statusMap.put("power_cut_alarm",          isActive(binary, 5));
            statusMap.put("fence_in_alarm",           isActive(binary, 6));
            statusMap.put("fence_out_alarm",          isActive(binary, 7));
            statusMap.put("acc_off",                  isActive(binary, 8));
            statusMap.put("acc_on",                   isActive(binary, 9));
            statusMap.put("gps_status",               isActive(binary, 10));
            statusMap.put("door_open",                isActive(binary, 11));
            statusMap.put("cut_off_oil",              isActive(binary, 12));
            statusMap.put("vehicle_battery_remove",   isActive(binary, 13));
            statusMap.put("anti_tamper_alarm",        isActive(binary, 14));
            statusMap.put("engine_status",            isActive(binary, 15));

        } catch (Exception e) {
            log.warn("Error parsing status bits: {}", e.getMessage());
        }

        return statusMap;
    }

    private boolean isActive(String binary, int lsbIndex) {
        if (lsbIndex < 0 || lsbIndex >= binary.length()) {
            return false;
        }
        return binary.charAt(binary.length() - 1 - lsbIndex) == '0';
    }

    private String getCurrentDateString() {
        // Returns current date as DDMMYY
        return String.format("%02d%02d%02d",
                java.time.LocalDate.now().getDayOfMonth(),
                java.time.LocalDate.now().getMonthValue(),
                java.time.LocalDate.now().getYear() % 100);
    }
}