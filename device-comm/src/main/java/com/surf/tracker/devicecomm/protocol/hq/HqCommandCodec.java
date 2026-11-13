package com.surf.tracker.devicecomm.protocol.hq;

import com.surf.tracker.devicecomm.exception.ProtocolException;
import com.surf.tracker.devicecomm.protocol.CommandCodec;
import com.surf.tracker.devicecomm.protocol.DeviceCommand;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Template-driven encoder for HQ-family commands: {@code %s} is the device id, then the HHmmss time. */
public final class HqCommandCodec implements CommandCodec {
    private static final Pattern DEVICE_ID = Pattern.compile("[0-9]{10,20}");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HHmmss").withZone(ZoneOffset.UTC);

    private final String protocolName;
    private final Map<DeviceCommand, String> templates;

    public HqCommandCodec(String protocolName, Map<DeviceCommand, String> templates) {
        this.protocolName = protocolName;
        this.templates = new EnumMap<>(templates);
    }

    @Override
    public Set<DeviceCommand> supported() {
        return templates.isEmpty() ? EnumSet.noneOf(DeviceCommand.class) : EnumSet.copyOf(templates.keySet());
    }

    @Override
    public byte[] encode(DeviceCommand command, String deviceId, Instant at) {
        String template = templates.get(command);
        if (template == null) throw ProtocolException.unsupportedCommand(protocolName, command.name());
        // The id is interpolated into the wire frame: anything but digits could inject fields or delimiters.
        if (deviceId == null || !DEVICE_ID.matcher(deviceId).matches())
            throw new ProtocolException("PROTOCOL_INVALID_DEVICE_ID", "Device id is not encodable for " + protocolName);
        return String.format(Locale.ROOT, template, deviceId, TIME.format(at)).getBytes(StandardCharsets.US_ASCII);
    }
}
