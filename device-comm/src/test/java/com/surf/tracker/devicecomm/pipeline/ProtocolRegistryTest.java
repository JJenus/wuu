package com.surf.tracker.devicecomm.pipeline;

import com.surf.tracker.devicecomm.exception.ProtocolException;
import com.surf.tracker.devicecomm.protocol.CommandCodec;
import com.surf.tracker.devicecomm.protocol.DeviceProtocol;
import com.surf.tracker.devicecomm.protocol.Frame;
import com.surf.tracker.devicecomm.protocol.FramingSpec;
import com.surf.tracker.devicecomm.protocol.autoseeker.AutoseekerProtocol;
import com.surf.tracker.devicecomm.protocol.gt06.Gt06Protocol;
import com.surf.tracker.devicecomm.protocol.model.DeviceMessage;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProtocolRegistryTest {
    private static final String TEN_DIGIT = "*HQ,8168000008,V1,043602,A,2234.9273,N,11354.3980,E,000.06,000,100715,FBFBBFF,460,00,10342,4283,10,25,128#";
    private static final String FIFTEEN_DIGIT = "*HQ,865205030330012,V1,145452,A,2240.55181,N,11358.32389,E,0.00,0,100815,FFFFFBFF#";

    private static Frame frame(String text) {
        return new Frame(text.getBytes(StandardCharsets.UTF_8));
    }

    private static DeviceProtocol stub(String name, FramingSpec framing) {
        return new DeviceProtocol() {
            @Override public String name() { return name; }
            @Override public FramingSpec framing() { return framing; }
            @Override public boolean recognizes(Frame frame) { return false; }
            @Override public List<DeviceMessage> decode(Frame frame, Instant receivedAt) { return List.of(); }
            @Override public CommandCodec commands() { return null; }
        };
    }

    @Test
    void detect_frameBothFamiliesMatch_prefersAutoseekerRegardlessOfRegistrationOrder() {
        // given: the 10-digit V1 frame matches both regexes; priority, not list order, decides
        var registry = new ProtocolRegistry(List.of(new Gt06Protocol(), new AutoseekerProtocol()));
        // when / then
        assertThat(registry.detect(frame(TEN_DIGIT)).name()).isEqualTo("Autoseeker");
    }

    @Test
    void detect_longerDeviceId_selectsGt06() {
        // given
        var registry = new ProtocolRegistry(List.of(new AutoseekerProtocol(), new Gt06Protocol()));
        // when / then
        assertThat(registry.detect(frame(FIFTEEN_DIGIT)).name()).isEqualTo("GT06");
    }

    @Test
    void detect_unrecognizedFrame_rejectsWithoutEchoingPayload() {
        // given
        var registry = new ProtocolRegistry(List.of(new AutoseekerProtocol()));
        String marker = "UNTRUSTED-CONTROL-MARKER";
        // when / then
        assertThatThrownBy(() -> registry.detect(frame("\r\n" + marker + "\u001b")))
                .isInstanceOf(ProtocolException.class).hasMessageNotContaining(marker);
        assertThatThrownBy(() -> registry.detect(frame("*"))).isInstanceOf(ProtocolException.class);
    }

    @Test
    void byName_isCaseInsensitive() {
        // given
        var registry = new ProtocolRegistry(List.of(new AutoseekerProtocol(), new Gt06Protocol()));
        // when / then
        assertThat(registry.byName("AUTOSEEKER")).isPresent();
        assertThat(registry.byName("gt06")).isPresent();
        assertThat(registry.byName("unknown")).isEmpty();
        assertThat(registry.byName(null)).isEmpty();
    }

    @Test
    void construct_noProtocols_failsFast() {
        // given / when / then
        assertThatThrownBy(() -> new ProtocolRegistry(List.of())).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void construct_duplicateNames_failsFast() {
        // given / when / then
        assertThatThrownBy(() -> new ProtocolRegistry(List.of(new Gt06Protocol(), new Gt06Protocol())))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Duplicate");
    }

    @Test
    void construct_protocolsWithDifferentFraming_failsFastAtStartup() {
        // given
        var newline = stub("Newline", new FramingSpec.Delimited((byte) '\n', 1024));
        // when / then
        assertThatThrownBy(() -> new ProtocolRegistry(List.of(new Gt06Protocol(), newline)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("framing");
    }

    @Test
    void framing_sharedByAllProtocols_isReturned() {
        // given
        var registry = new ProtocolRegistry(List.of(new AutoseekerProtocol(), new Gt06Protocol()));
        // when / then
        assertThat(registry.framing()).isEqualTo(new FramingSpec.Delimited((byte) '#', 1024));
    }
}
