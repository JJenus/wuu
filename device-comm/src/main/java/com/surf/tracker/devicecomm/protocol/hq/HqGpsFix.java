package com.surf.tracker.devicecomm.protocol.hq;

import com.surf.tracker.shared.domain.LocationPoint;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoField;
import java.util.Locale;

/**
 * Strict GPS fix decoding for the HQ family. Anything unparseable, out of range, or ambiguous in the
 * configured timezone is rejected; no value is ever invented.
 */
public final class HqGpsFix {
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            new DateTimeFormatterBuilder().appendPattern("ddMM")
                    .appendValueReduced(ChronoField.YEAR, 2, 2, 2000).appendPattern("HHmmss")
                    .toFormatter(Locale.ROOT).withResolverStyle(ResolverStyle.STRICT);

    private HqGpsFix() {
    }

    /**
     * Fields from {@code start}: validity, lat, N/S, lon, E/W, speed(knots), heading, date.
     *
     * @param time               HHmmss field for this fix
     * @param combinedDateStatus the date field may have a hex status word appended (command responses)
     */
    public static LocationPoint parse(String[] parts, int start, String time, boolean combinedDateStatus, ZoneId zone) {
        try {
            return parseUnchecked(parts, start, time, combinedDateStatus, zone);
        } catch (HqFixException known) {
            throw known;
        } catch (RuntimeException unexpected) {
            // NumberFormatException / DateTimeParseException messages embed the offending payload.
            throw new HqFixException("Malformed GPS fix");
        }
    }

    private static LocationPoint parseUnchecked(String[] parts, int start, String time,
                                                boolean combinedDateStatus, ZoneId zone) {
        if (parts.length <= start + 7 || !"A".equals(parts[start]))
            throw new HqFixException("Packet does not contain a valid GPS fix");
        double latitude = coordinate(parts[start + 1], parts[start + 2], true);
        double longitude = coordinate(parts[start + 3], parts[start + 4], false);
        String speedText = parts[start + 5];
        if (!speedText.matches("[0-9]+(?:\\.[0-9]+)?")) throw new HqFixException("Invalid speed");
        float speed = Float.parseFloat(speedText) * 1.852f;
        if (!Float.isFinite(speed)) throw new HqFixException("Speed is not finite");
        String headingText = parts[start + 6];
        if (!headingText.matches("[0-9]+(?:\\.[0-9]+)?")) throw new HqFixException("Invalid heading");
        double heading = Double.parseDouble(headingText);
        if (!Double.isFinite(heading) || heading >= 360) throw new HqFixException("Heading outside [0,360)");
        String date = parts[start + 7];
        if (combinedDateStatus && date.matches("[0-9]{6}[0-9A-Fa-f]{1,8}")) date = date.substring(0, 6);
        return new LocationPoint(latitude, longitude, speed, timestamp(date, time, zone));
    }

    private static double coordinate(String text, String direction, boolean latitude) {
        int degreesLength = latitude ? 2 : 3;
        String normalized = text;
        if (!text.contains(".") && text.length() > degreesLength + 2 && text.matches("[0-9]+")) {
            normalized = text.substring(0, degreesLength + 2) + "." + text.substring(degreesLength + 2);
        }
        if (!normalized.matches("[0-9]{" + (degreesLength + 2) + "}(?:\\.[0-9]+)?"))
            throw new HqFixException("Invalid coordinate representation");
        boolean negative = latitude ? "S".equals(direction) : "W".equals(direction);
        boolean positive = latitude ? "N".equals(direction) : "E".equals(direction);
        if (!negative && !positive) throw new HqFixException("Invalid coordinate hemisphere");
        int degrees = Integer.parseInt(normalized.substring(0, degreesLength));
        double minutes = Double.parseDouble(normalized.substring(degreesLength));
        int maximum = latitude ? 90 : 180;
        if (!Double.isFinite(minutes) || minutes >= 60 || degrees > maximum || (degrees == maximum && minutes > 0))
            throw new HqFixException("Coordinate outside valid range");
        double value = degrees + minutes / 60.0;
        return negative ? -value : value;
    }

    private static Instant timestamp(String date, String time, ZoneId zone) {
        if (!date.matches("[0-9]{6}") || !time.matches("[0-9]{6}")) throw new HqFixException("Invalid GPS date or time");
        LocalDateTime local = LocalDateTime.parse(date + time, DATE_TIME_FORMATTER);
        var offsets = zone.getRules().getValidOffsets(local);
        if (offsets.size() != 1) throw new HqFixException("GPS time is nonexistent or ambiguous in configured timezone");
        return local.toInstant(offsets.getFirst());
    }
}
