package com.surf.tracker.devicecomm.protocol;

import com.surf.tracker.devicecomm.exception.ProtocolException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DelimitedFrameDecoderTest {
    private static byte[] ascii(String text) {
        return text.getBytes(StandardCharsets.US_ASCII);
    }

    @Test
    void feed_frameSplitAcrossChunks_emitsOnceWithDelimiterKept() {
        // given
        var decoder = new DelimitedFrameDecoder(new FramingSpec.Delimited((byte) '#', 64));
        byte[] all = ascii("*HQ,1#*HQ,2#*HQ,");
        List<Frame> frames = new ArrayList<>();
        // when: three-byte chunks cut frames at arbitrary points
        for (int i = 0; i < all.length; i += 3)
            frames.addAll(decoder.feed(Arrays.copyOfRange(all, i, Math.min(all.length, i + 3))));
        // then
        assertThat(frames).extracting(Frame::ascii).containsExactly("*HQ,1#", "*HQ,2#");
        assertThat(decoder.pending()).isEqualTo(4);
    }

    @Test
    void feed_frameExactlyAtLimit_isAccepted() {
        // given
        var decoder = new DelimitedFrameDecoder(new FramingSpec.Delimited((byte) '#', 6));
        // when / then
        assertThat(decoder.feed(ascii("abcde#"))).hasSize(1);
    }

    @Test
    void feed_frameBeyondLimit_failsTheStream() {
        // given
        var decoder = new DelimitedFrameDecoder(new FramingSpec.Delimited((byte) '#', 6));
        // when / then
        assertThatThrownBy(() -> decoder.feed(ascii("abcdefg"))).isInstanceOf(ProtocolException.class);
    }

    @Test
    void construct_invalidBound_rejectsConfiguration() {
        // given / when / then
        assertThatThrownBy(() -> new FramingSpec.Delimited((byte) '#', 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
