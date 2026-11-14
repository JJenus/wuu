package com.surf.tracker.devicecomm.protocol.phone;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.surf.tracker.devicecomm.exception.ProtocolException;
import com.surf.tracker.devicecomm.protocol.CommandCodec;
import com.surf.tracker.devicecomm.protocol.DeviceProtocol;
import com.surf.tracker.devicecomm.protocol.Frame;
import com.surf.tracker.devicecomm.protocol.FramingSpec;
import com.surf.tracker.devicecomm.protocol.Transport;
import com.surf.tracker.devicecomm.protocol.model.DeviceMessage;
import com.surf.tracker.devicecomm.protocol.model.PositionReport;
import com.surf.tracker.shared.domain.DeviceHealth;
import com.surf.tracker.shared.domain.LocationPoint;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * A phone or browser reporting its own geolocation as JSON over an authenticated WebSocket:
 * {@code {"lat":6.5,"lng":3.3,"speed_kmh":12.5,"accuracy_m":8,"ts":1760000000000,"battery_pct":80}}.
 * Required: lat, lng, speed_kmh, ts (epoch ms). The device id never comes from the payload; it is the
 * identity the transport already proved, so a client cannot speak as another tracker.
 */
public final class PhoneProtocol implements DeviceProtocol {
    public static final String NAME = "PHONE";
    private static final Duration MAX_AGE = Duration.ofHours(1);
    private static final Duration MAX_FUTURE_SKEW = Duration.ofMinutes(5);
    private static final double MAX_SPEED_KMH = 500;
    private static final ObjectMapper JSON = new ObjectMapper();

    private final int maxAccuracyMeters;

    public PhoneProtocol(int maxAccuracyMeters) {
        if (maxAccuracyMeters < 1) throw new IllegalArgumentException("Accuracy bound must be positive");
        this.maxAccuracyMeters = maxAccuracyMeters;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Transport transport() {
        return Transport.WEBSOCKET;
    }

    @Override
    public FramingSpec framing() {
        return new FramingSpec.Preframed(1024);
    }

    @Override
    public boolean recognizes(Frame frame) {
        return frame.authenticatedDeviceId() != null;
    }

    @Override
    public List<DeviceMessage> decode(Frame frame, Instant receivedAt) {
        String deviceId = frame.authenticatedDeviceId();
        if (deviceId == null) throw ProtocolException.parseError(NAME, "frame is not from an authenticated session");
        JsonNode root = parse(frame);
        double latitude = number(root, "lat");
        double longitude = number(root, "lng");
        double speedKmh = number(root, "speed_kmh");
        long epochMillis = (long) number(root, "ts");
        Double accuracy = optional(root, "accuracy_m");
        Double battery = optional(root, "battery_pct");

        if (speedKmh < 0 || speedKmh > MAX_SPEED_KMH) throw ProtocolException.parseError(NAME, "speed out of range");
        if (accuracy != null && (accuracy < 0 || accuracy > maxAccuracyMeters))
            throw ProtocolException.parseError(NAME, "fix too imprecise");
        if (battery != null && (battery < 0 || battery > 100)) throw ProtocolException.parseError(NAME, "battery out of range");
        // The device clock is trusted for ordering only inside a sane window; never substitute the receive time.
        Instant timestamp = Instant.ofEpochMilli(epochMillis);
        if (timestamp.isBefore(receivedAt.minus(MAX_AGE)) || timestamp.isAfter(receivedAt.plus(MAX_FUTURE_SKEW)))
            throw ProtocolException.parseError(NAME, "timestamp outside accepted window");

        LocationPoint point = new LocationPoint(latitude, longitude, (float) speedKmh, timestamp);
        if (!point.isValid()) throw ProtocolException.parseError(NAME, "invalid coordinates");
        DeviceHealth health = battery == null ? DeviceHealth.NONE
                : DeviceHealth.ofBattery((int) Math.round(battery), null);
        return List.of(new PositionReport(deviceId, receivedAt, point, health));
    }

    @Override
    public CommandCodec commands() {
        return CommandCodec.NONE;
    }

    private static JsonNode parse(Frame frame) {
        try {
            JsonNode root = JSON.readTree(frame.bytes());
            if (root == null || !root.isObject()) throw ProtocolException.parseError(NAME, "payload is not an object");
            return root;
        } catch (IOException malformed) {
            throw ProtocolException.parseError(NAME, "payload is not valid JSON");
        }
    }

    private static double number(JsonNode root, String field) {
        JsonNode value = root.get(field);
        if (value == null || !value.isNumber() || !Double.isFinite(value.doubleValue()))
            throw ProtocolException.parseError(NAME, "missing or non-numeric " + field);
        return value.doubleValue();
    }

    private static Double optional(JsonNode root, String field) {
        JsonNode value = root.get(field);
        if (value == null || value.isNull()) return null;
        if (!value.isNumber() || !Double.isFinite(value.doubleValue()))
            throw ProtocolException.parseError(NAME, "non-numeric " + field);
        return value.doubleValue();
    }
}
