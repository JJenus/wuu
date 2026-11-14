package com.surf.tracker.devicecomm.protocol;

import com.surf.tracker.devicecomm.protocol.model.DeviceMessage;

import java.time.Instant;
import java.util.List;

/**
 * The single contract a new device protocol implements. Implementations must be stateless and
 * thread-safe: one instance serves every connection, and per-connection state lives in the pipeline.
 *
 * <p>Register an implementation as a Spring bean and it is picked up by the registry; see
 * {@code device-comm/PROTOCOLS.md}.
 */
public interface DeviceProtocol {

    /** Stable name; also the value stored in {@code Tracker.protocol} (matched case-insensitively). */
    String name();

    /** Lower values are asked first when auto-detecting a connection's protocol. */
    default int detectionPriority() {
        return 100;
    }

    /** Which listener serves this protocol. */
    default Transport transport() {
        return Transport.TCP;
    }

    /** How this protocol's frames are cut from the stream (or bounded, for pre-framed transports). */
    FramingSpec framing();

    /** Cheap structural check used only on a connection's first frame; must not throw. */
    boolean recognizes(Frame frame);

    /**
     * Decodes one frame into typed messages. All messages of a frame must carry the same device id.
     *
     * @param receivedAt server receive time, for messages without a trustworthy device clock
     * @throws com.surf.tracker.devicecomm.exception.ProtocolException when the frame is malformed;
     *         never invent a value for a field that cannot be parsed
     */
    List<DeviceMessage> decode(Frame frame, Instant receivedAt);

    CommandCodec commands();
}
