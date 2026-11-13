package com.surf.tracker.devicecomm.protocol;

import com.surf.tracker.devicecomm.exception.ProtocolException;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/** One complete wire frame, exactly as cut by the protocol's {@link FramingSpec}. */
public record Frame(byte[] bytes) {
    public Frame {
        if (bytes == null || bytes.length == 0) throw new IllegalArgumentException("A frame cannot be empty");
        bytes = bytes.clone();
    }

    @Override
    public byte[] bytes() {
        return bytes.clone();
    }

    public int length() {
        return bytes.length;
    }

    /** Strict US-ASCII view for text protocols; malformed bytes reject the frame instead of becoming '?'. */
    public String ascii() {
        try {
            return StandardCharsets.US_ASCII.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            throw new ProtocolException("PROTOCOL_NOT_ASCII", "Frame contains non-ASCII bytes");
        }
    }
}
