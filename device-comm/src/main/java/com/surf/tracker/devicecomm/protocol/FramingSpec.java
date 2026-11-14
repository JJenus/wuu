package com.surf.tracker.devicecomm.protocol;

/**
 * How a protocol cuts a byte stream into frames. Sealed so that adding a variant (for example
 * length-prefixed binary) forces every framer to handle it.
 */
public sealed interface FramingSpec {

    /** Frames end with {@code delimiter}, which is kept as the frame's last byte. */
    record Delimited(byte delimiter, int maxLength) implements FramingSpec {
        public Delimited {
            if (maxLength < 1 || maxLength > 65536)
                throw new IllegalArgumentException("Frame length bound must be between 1 and 65536");
        }
    }

    /**
     * The transport already delivers one message per frame (WebSocket), so no byte-stream cutting is
     * needed; only an upper bound on message size.
     */
    record Preframed(int maxLength) implements FramingSpec {
        public Preframed {
            if (maxLength < 1 || maxLength > 65536)
                throw new IllegalArgumentException("Frame length bound must be between 1 and 65536");
        }
    }
}
