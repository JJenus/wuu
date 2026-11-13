package com.surf.tracker.devicecomm.protocol;

import com.surf.tracker.devicecomm.exception.ProtocolException;
import com.surf.tracker.devicecomm.protocol.model.DeviceMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Every {@link DeviceProtocol} must extend this and supply real sample frames. It pins the guarantees
 * the pipeline relies on, so a new protocol is safe to plug in once this passes.
 */
public abstract class DeviceProtocolContractTest {
    private static final Instant RECEIVED_AT = Instant.parse("2026-11-13T00:00:00Z");

    protected abstract DeviceProtocol protocol();

    /** Frames the protocol must recognize and decode, one device per frame. */
    protected abstract List<String> validFrames();

    protected static Frame frame(String text) {
        return new Frame(text.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void recognizes_everyValidSample_isTrue() {
        // given / when / then
        for (String sample : validFrames()) assertThat(protocol().recognizes(frame(sample))).as(sample).isTrue();
    }

    @Test
    void decode_everyValidSample_returnsMessagesForExactlyOneDevice() {
        // given / when / then
        for (String sample : validFrames()) {
            List<DeviceMessage> messages = protocol().decode(frame(sample), RECEIVED_AT);
            assertThat(messages).as(sample).isNotEmpty();
            assertThat(messages.stream().map(DeviceMessage::deviceId).distinct()).as(sample).hasSize(1);
            assertThat(messages).allSatisfy(message -> assertThat(message.receivedAt()).isNotNull());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"x", "*", "#", "*#", "*HQ,#", "*HQ,1,#", ",,,,#", "garbage#", "*HQ,8168000008,V1#"})
    void decode_garbage_rejectsWithProtocolExceptionOnly(String garbage) {
        // given / when / then
        assertThatThrownBy(() -> protocol().decode(frame(garbage), RECEIVED_AT)).isInstanceOf(ProtocolException.class);
    }

    @Test
    void recognizes_arbitraryBytes_neverThrows() {
        // given: deterministic fuzz
        Random random = new Random(42);
        for (int i = 0; i < 500; i++) {
            byte[] bytes = new byte[1 + random.nextInt(80)];
            random.nextBytes(bytes);
            // when / then
            assertThat(protocol().recognizes(new Frame(bytes))).isNotNull();
        }
    }

    @Test
    void decode_concurrentCallsOnSameInstance_areIndependent() throws Exception {
        // given: one instance serves every connection, so it must hold no per-frame state
        String sample = validFrames().getFirst();
        List<DeviceMessage> expected = protocol().decode(frame(sample), RECEIVED_AT);
        List<Callable<List<DeviceMessage>>> calls = new ArrayList<>();
        for (int i = 0; i < 64; i++) calls.add(() -> protocol().decode(frame(sample), RECEIVED_AT));
        var executor = Executors.newFixedThreadPool(8);
        try {
            // when / then
            for (var result : executor.invokeAll(calls)) assertThat(result.get()).isEqualTo(expected);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void framing_isDelimitedAndStable() {
        // given / when / then
        assertThat(protocol().framing()).isInstanceOf(FramingSpec.Delimited.class).isEqualTo(protocol().framing());
    }

    @Test
    void commands_everySupportedCommand_encodesNonEmptyAsciiTerminatedByDelimiter() {
        // given
        var delimiter = ((FramingSpec.Delimited) protocol().framing()).delimiter();
        // when / then
        for (DeviceCommand command : protocol().commands().supported()) {
            byte[] wire = protocol().commands().encode(command, "865205030330012", RECEIVED_AT);
            assertThat(wire).isNotEmpty().endsWith(delimiter);
            assertThat(new String(wire, StandardCharsets.US_ASCII)).contains("865205030330012");
        }
    }

    @Test
    void commands_deviceIdThatCouldInjectFields_isRejected() {
        // given / when / then
        for (DeviceCommand command : protocol().commands().supported())
            assertThatThrownBy(() -> protocol().commands().encode(command, "1234567890,S2,1#", RECEIVED_AT))
                    .isInstanceOf(ProtocolException.class);
    }
}
