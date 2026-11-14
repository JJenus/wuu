package com.surf.tracker.devicecomm.protocol.phone;

import com.surf.tracker.devicecomm.exception.ProtocolException;
import com.surf.tracker.devicecomm.protocol.Frame;
import com.surf.tracker.devicecomm.protocol.FramingSpec;
import com.surf.tracker.devicecomm.protocol.Transport;
import com.surf.tracker.devicecomm.protocol.model.PositionReport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PhoneProtocolTest {
    private static final Instant NOW = Instant.parse("2026-11-13T00:00:00Z");
    private static final String DEVICE = "2348000000001";
    private static final String FIX = "{\"lat\":6.5244,\"lng\":3.3792,\"speed_kmh\":12.5,\"accuracy_m\":8,\"ts\":%d,\"battery_pct\":80}";
    private final PhoneProtocol protocol = new PhoneProtocol(100);

    @Test
    void metadata_isWebSocketPreframed() {
        assertThat(protocol.transport()).isEqualTo(Transport.WEBSOCKET);
        assertThat(protocol.framing()).isInstanceOf(FramingSpec.Preframed.class);
        assertThat(protocol.commands().supported()).isEmpty();
    }

    @Test
    void decode_validFix_returnsPositionWithBatteryForTheAuthenticatedDevice() {
        // given
        var frame = authenticated(FIX.formatted(NOW.toEpochMilli()), DEVICE);
        // when
        var report = (PositionReport) protocol.decode(frame, NOW).getFirst();
        // then
        assertThat(report.deviceId()).isEqualTo(DEVICE);
        assertThat(report.point().latitude()).isEqualTo(6.5244);
        assertThat(report.point().speedKmh()).isEqualTo(12.5f);
        assertThat(report.point().timestamp()).isEqualTo(NOW);
        assertThat(report.health().batteryPercent()).isEqualTo(80);
    }

    @Test
    void decode_payloadClaimingAnotherDevice_isIgnoredInFavourOfAuthenticatedIdentity() {
        // given
        var frame = authenticated("{\"device_id\":\"9999999999\",\"lat\":1,\"lng\":2,\"speed_kmh\":0,\"ts\":" + NOW.toEpochMilli() + "}", DEVICE);
        // when / then
        assertThat(protocol.decode(frame, NOW).getFirst().deviceId()).isEqualTo(DEVICE);
    }

    @Test
    void recognizes_unauthenticatedFrame_isFalseSoRawPeersCannotUseIt() {
        assertThat(protocol.recognizes(new Frame(FIX.formatted(NOW.toEpochMilli()).getBytes(StandardCharsets.UTF_8)))).isFalse();
    }

    @Test
    void decode_unauthenticatedFrame_isRejected() {
        var frame = new Frame(FIX.formatted(NOW.toEpochMilli()).getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> protocol.decode(frame, NOW)).isInstanceOf(ProtocolException.class);
    }

    @Test
    void decode_missingSpeed_isRejectedRatherThanInvented() {
        var frame = authenticated("{\"lat\":1,\"lng\":2,\"ts\":" + NOW.toEpochMilli() + "}", DEVICE);
        assertThatThrownBy(() -> protocol.decode(frame, NOW)).isInstanceOf(ProtocolException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "not json", "[]", "null", "{}",
            "{\"lat\":\"6.5\",\"lng\":3,\"speed_kmh\":1,\"ts\":1794528000000}",
            "{\"lat\":91,\"lng\":3,\"speed_kmh\":1,\"ts\":1794528000000}",
            "{\"lat\":6,\"lng\":181,\"speed_kmh\":1,\"ts\":1794528000000}",
            "{\"lat\":6,\"lng\":3,\"speed_kmh\":-1,\"ts\":1794528000000}",
            "{\"lat\":6,\"lng\":3,\"speed_kmh\":501,\"ts\":1794528000000}",
            "{\"lat\":6,\"lng\":3,\"speed_kmh\":1,\"ts\":1794528000000,\"accuracy_m\":101}",
            "{\"lat\":6,\"lng\":3,\"speed_kmh\":1,\"ts\":1794528000000,\"battery_pct\":101}",
            "{\"lat\":6,\"lng\":3,\"speed_kmh\":1,\"ts\":1794528000000,\"battery_pct\":\"full\"}",
            "{\"lat\":6,\"lng\":3,\"speed_kmh\":1,\"ts\":1}",
            "{\"lat\":6,\"lng\":3,\"speed_kmh\":1,\"ts\":99999999999999}"})
    void decode_invalidPayload_isRejectedWithoutEchoingIt(String payload) {
        // given
        var frame = authenticated(payload, DEVICE);
        // when / then
        assertThatThrownBy(() -> protocol.decode(frame, NOW)).isInstanceOf(ProtocolException.class)
                .satisfies(failure -> assertThat(failure.getMessage()).doesNotContain(payload));
    }

    @Test
    void decode_futureTimestampBeyondSkew_isRejected() {
        var frame = authenticated(FIX.formatted(NOW.plusSeconds(600).toEpochMilli()), DEVICE);
        assertThatThrownBy(() -> protocol.decode(frame, NOW)).isInstanceOf(ProtocolException.class);
    }

    private static Frame authenticated(String json, String deviceId) {
        return new Frame(json.getBytes(StandardCharsets.UTF_8), deviceId);
    }
}
