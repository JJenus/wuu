package com.surf.tracker.devicecomm.protocol;

import com.surf.tracker.devicecomm.exception.ProtocolException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Per-connection stream splitter for {@link FramingSpec.Delimited}. Not thread-safe; one per connection. */
public final class DelimitedFrameDecoder {
    private final byte delimiter;
    private final byte[] buffer;
    private int size;

    public DelimitedFrameDecoder(FramingSpec.Delimited spec) {
        this.delimiter = spec.delimiter();
        this.buffer = new byte[spec.maxLength()];
    }

    /**
     * Consumes a network chunk and returns every frame it completes. A frame that would exceed
     * the bound fails the whole stream, because the next frame boundary can no longer be trusted.
     */
    public List<Frame> feed(byte[] chunk) {
        List<Frame> frames = new ArrayList<>();
        for (byte b : chunk) {
            if (size == buffer.length) throw ProtocolException.frameTooLarge(buffer.length);
            buffer[size++] = b;
            if (b == delimiter) {
                frames.add(new Frame(Arrays.copyOf(buffer, size)));
                size = 0;
            }
        }
        return frames;
    }

    /** Bytes of an unfinished frame currently buffered. */
    public int pending() {
        return size;
    }
}
