package com.surf.tracker.devicecomm.pipeline;

import com.surf.tracker.devicecomm.exception.ProtocolException;
import com.surf.tracker.devicecomm.protocol.DeviceProtocol;
import com.surf.tracker.devicecomm.protocol.Frame;
import com.surf.tracker.devicecomm.protocol.model.DeviceMessage;
import com.surf.tracker.devicecomm.protocol.model.LoginReport;
import com.surf.tracker.devicecomm.protocol.model.PositionReport;
import com.surf.tracker.devicecomm.protocol.model.StatusReport;
import com.surf.tracker.shared.events.LocationDataEvent;
import com.surf.tracker.shared.exception.InfrastructureException;
import com.surf.tracker.shared.pubsub.EventPublisher;
import com.surf.tracker.shared.util.TimeProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * The one flow every protocol shares: detect once per connection, decode, verify identity, route.
 * Protocols only decode; nothing here is protocol-specific.
 */
@Service
public class IngestPipeline {
    private static final Logger log = LoggerFactory.getLogger(IngestPipeline.class);

    private final ProtocolRegistry registry;
    private final EventPublisher publisher;
    private final Clock clock;

    public IngestPipeline(ProtocolRegistry registry, EventPublisher publisher, Clock clock) {
        this.registry = registry;
        this.publisher = publisher;
        this.clock = clock;
    }

    /**
     * @return the device id the frame proved
     * @throws ProtocolException       the frame was rejected; the connection may continue
     * @throws InfrastructureException publication failed; the connection should be closed
     */
    public String ingest(Frame frame, Instant receivedAt, ConnectionSession session) {
        DeviceProtocol protocol = session.protocol() != null ? session.protocol() : registry.detect(frame);
        List<DeviceMessage> messages = decode(protocol, frame, receivedAt);
        String deviceId = singleDeviceId(protocol, messages);
        session.requireIdentity(deviceId);

        for (DeviceMessage message : messages) route(protocol, deviceId, message);

        session.accept(protocol, deviceId);
        return deviceId;
    }

    private List<DeviceMessage> decode(DeviceProtocol protocol, Frame frame, Instant receivedAt) {
        try {
            return protocol.decode(frame, receivedAt);
        } catch (ProtocolException rejected) {
            throw rejected;
        } catch (RuntimeException unexpected) {
            // A protocol leaking e.g. NumberFormatException must not look like a broker outage, and
            // its message may embed device payload, so it is deliberately not propagated.
            throw ProtocolException.parseError(protocol.name(), "malformed frame");
        }
    }

    private static String singleDeviceId(DeviceProtocol protocol, List<DeviceMessage> messages) {
        if (messages.isEmpty()) throw ProtocolException.parseError(protocol.name(), "frame produced no messages");
        String deviceId = messages.getFirst().deviceId();
        if (deviceId == null || messages.stream().anyMatch(message -> !deviceId.equals(message.deviceId())))
            throw ProtocolException.parseError(protocol.name(), "frame does not identify exactly one device");
        return deviceId;
    }

    private void route(DeviceProtocol protocol, String deviceId, DeviceMessage message) {
        // Exhaustive: a new message kind will not compile until its routing is decided here.
        switch (message) {
            case PositionReport position -> publish(protocol, deviceId, position);
            case StatusReport ignored -> log.debug("Status report from {} ({}) not published", deviceId, protocol.name());
            case LoginReport ignored -> log.debug("Login from {} ({})", deviceId, protocol.name());
        }
    }

    private void publish(DeviceProtocol protocol, String deviceId, PositionReport position) {
        try {
            publisher.publish(new LocationDataEvent(clock, TimeProvider.newUuid(), deviceId,
                    position.point(), protocol.name(), null));
        } catch (RuntimeException failure) {
            throw new InfrastructureException("DEVICE_DATA_PROCESS_ERROR",
                    "Failed to publish telemetry for " + deviceId, failure);
        }
    }
}
