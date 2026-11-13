package com.surf.tracker.devicecomm.pipeline;

import com.surf.tracker.devicecomm.exception.ProtocolException;
import com.surf.tracker.devicecomm.protocol.DeviceProtocol;
import com.surf.tracker.devicecomm.protocol.Frame;
import com.surf.tracker.devicecomm.protocol.FramingSpec;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** Every {@link DeviceProtocol} bean, ordered by detection priority. Adding a protocol needs no edit here. */
@Component
public class ProtocolRegistry {
    private final List<DeviceProtocol> protocols;

    public ProtocolRegistry(List<DeviceProtocol> protocols) {
        if (protocols.isEmpty()) throw new IllegalStateException("At least one device protocol must be registered");
        this.protocols = protocols.stream()
                .sorted(Comparator.comparingInt(DeviceProtocol::detectionPriority)).toList();
        Set<String> names = new HashSet<>();
        for (DeviceProtocol protocol : this.protocols) {
            if (!names.add(protocol.name().toLowerCase(Locale.ROOT)))
                throw new IllegalStateException("Duplicate device protocol name: " + protocol.name());
        }
        framing(); // fail fast at startup, not on the first connection
    }

    /** First protocol, in priority order, that recognizes the frame. */
    public DeviceProtocol detect(Frame frame) {
        return protocols.stream().filter(protocol -> protocol.recognizes(frame)).findFirst()
                .orElseThrow(ProtocolException::parserNotFound);
    }

    public Optional<DeviceProtocol> byName(String name) {
        return protocols.stream().filter(protocol -> protocol.name().equalsIgnoreCase(name)).findFirst();
    }

    /**
     * Framing is needed before a connection's protocol is known, so all protocols on one listener must
     * share it. A protocol with different framing needs its own listener port.
     */
    public FramingSpec framing() {
        FramingSpec shared = protocols.getFirst().framing();
        for (DeviceProtocol protocol : protocols) {
            if (!shared.equals(protocol.framing()))
                throw new IllegalStateException("Protocols on one listener must share framing; "
                        + protocol.name() + " differs from " + protocols.getFirst().name());
        }
        return shared;
    }
}
