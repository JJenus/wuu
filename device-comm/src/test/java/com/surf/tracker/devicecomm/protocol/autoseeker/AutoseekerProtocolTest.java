package com.surf.tracker.devicecomm.protocol.autoseeker;

import com.surf.tracker.devicecomm.exception.ProtocolException;
import com.surf.tracker.devicecomm.protocol.DeviceCommand;
import com.surf.tracker.devicecomm.protocol.Frame;
import com.surf.tracker.shared.domain.DeviceAlarm;
import com.surf.tracker.devicecomm.protocol.model.PositionReport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class AutoseekerProtocolTest {
    private static final String VALID = "*HQ,8168000008,V1,043602,A,2234.9273,N,11354.3980,E,000.06,000,100715,FBFBBFF,460,00,10342,4283,10,25,128#";
    private static final Instant RECEIVED = Instant.parse("2026-11-13T00:00:00Z");
    private final AutoseekerProtocol protocol = new AutoseekerProtocol();

    private static Frame frame(String text) {
        return new Frame(text.getBytes(StandardCharsets.UTF_8));
    }

    private PositionReport decode(AutoseekerProtocol under, String text) {
        return (PositionReport) under.decode(frame(text), RECEIVED).getFirst();
    }

    @Test
    void decode_validFrame_returnsPositionWithDeviceAndHealth() {
        // given / when
        PositionReport report = decode(protocol, VALID);
        // then
        assertThat(report.deviceId()).isEqualTo("8168000008");
        assertThat(report.point().latitude()).isCloseTo(22.5821, within(0.0001));
        assertThat(report.point().longitude()).isCloseTo(113.9066, within(0.0001));
        assertThat(report.point().speedKmh()).isCloseTo(0.06f * 1.852f, within(0.001f));
        assertThat(report.health().gpsSignal()).isEqualTo(10);
        assertThat(report.health().gsmSignal()).isEqualTo(25);
        assertThat(report.health().alarms()).isEmpty();
    }

    @Test
    void decode_statusWordWithClearedBits_reportsMappedAlarms() {
        // given: bits 3 and 4 cleared (active-low)
        String frame = VALID.replace("FBFBBFF", "FBFBBE7");
        // when / then
        assertThat(decode(protocol, frame).health().alarms())
                .isEqualTo(EnumSet.of(DeviceAlarm.OIL_CUT, DeviceAlarm.BATTERY_REMOVED));
    }

    @ParameterizedTest
    @CsvSource({"5,garbage", "5,NaN", "5,Infinity", "5,-2234.9273", "5,2260.0000",
            "5,9000.0001", "7,18000.0001", "7,18100.0000", "7,11360.0000", "6,E", "8,N",
            "9,-1", "9,NaN", "9,Infinity", "9,1e40", "10,360", "10,-1", "10,NaN",
            "3,246000", "11,310226", "11,000000", "4,X", "4,V"})
    void decode_malformedTelemetry_rejectsInsteadOfInventingPosition(int index, String value) {
        // given
        String[] fields = VALID.substring(0, VALID.length() - 1).split(",", -1);
        fields[index] = value;
        String packet = String.join(",", fields) + "#";
        // when / then
        assertThatThrownBy(() -> protocol.decode(frame(packet), RECEIVED)).isInstanceOf(ProtocolException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"\u0000", "\u001b", "é", "#extra", "\r", "\n"})
    void decode_controlNonAsciiOrEmbeddedDelimiter_rejectsFrame(String injected) {
        // given
        String packet = VALID.replace("FBFBBFF", "FBF" + injected + "BBFF");
        // when / then
        assertThatThrownBy(() -> protocol.decode(frame(packet), RECEIVED)).isInstanceOf(ProtocolException.class);
        assertThat(protocol.recognizes(frame(packet))).isFalse();
    }

    @Test
    void decode_oversizedFrame_rejectsBeforeSplitting() {
        // given
        String packet = VALID.replace("FBFBBFF", "F".repeat(2000));
        // when / then
        assertThatThrownBy(() -> protocol.decode(frame(packet), RECEIVED)).isInstanceOf(ProtocolException.class);
    }

    @Test
    void decode_validFrame_usesUtcIndependentOfHostTimezone() {
        // given
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Singapore"));
            // when
            PositionReport report = decode(protocol, VALID);
            // then
            assertThat(report.point().timestamp()).isEqualTo(Instant.parse("2015-07-10T04:36:02Z"));
            assertThat(report.point().isValid()).isTrue();
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    void decode_polesAndDateLine_acceptsExactBoundaries() {
        // given
        String packet = VALID.replace("2234.9273,N,11354.3980,E", "9000.0000,S,18000.0000,W");
        // when
        PositionReport report = decode(protocol, packet);
        // then
        assertThat(report.point().latitude()).isEqualTo(-90);
        assertThat(report.point().longitude()).isEqualTo(-180);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "*HQ,8168000008,V4,D1,30,65535,043600,043602,A,2234.9273,N,11354.3980,E,000.06,000,100715,FFFFBBFF,460,00,10342,3721#",
            "*HQ,8168000008,V4,S20,DONE,043600,043602,A,2234.9273,N,11354.3980,E,000.06,000,100715F7FFBBFF,460,00,10342,3721#",
            "*HQ,8168000008,V4,SCF,043600,043602,A,2234.9273,N,11354.3980,E,000.06,000,100715,FFFFB9FF,460,00,10342,3721#"
    })
    void decode_documentedCommandResponses_preservesActualGpsFix(String packet) {
        // given: formats already emitted by the repository's Autoseeker simulator.
        // when
        PositionReport report = decode(protocol, packet);
        // then
        assertThat(report.point().timestamp()).isEqualTo(Instant.parse("2015-07-10T04:36:02Z"));
        assertThat(report.point().latitude()).isCloseTo(22.5821216667, within(0.000001));
        assertThat(report.point().longitude()).isCloseTo(113.9066333333, within(0.000001));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "*HQ,8168000008,V4,D1,30,65535,043600,043602,A,NaN,N,11354.3980,E,000.06,000,100715#",
            "*HQ,8168000008,V4,S20,DONE,043600,043602,A,2234.9273,N,11354.3980,E,000.06,000,310226F7FFBBFF#",
            "*HQ,8168000008,V4,SCF,043600,043602,A,2234.9273,N,11354.3980,E,000.06,000#"
    })
    void decode_malformedCommandResponse_rejectsWithoutSyntheticFix(String packet) {
        // given / when / then
        assertThatThrownBy(() -> protocol.decode(frame(packet), RECEIVED)).isInstanceOf(ProtocolException.class);
    }

    @Test
    void decode_configuredZone_usesExplicitOffset() {
        // given / when
        PositionReport report = decode(new AutoseekerProtocol(ZoneId.of("Asia/Singapore"), 1024), VALID);
        // then
        assertThat(report.point().timestamp()).isEqualTo(Instant.parse("2015-07-09T20:36:02Z"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"290326", "251026"})
    void decode_nonexistentOrAmbiguousLocalTime_rejectsInsteadOfGuessing(String date) {
        // given
        var regional = new AutoseekerProtocol(ZoneId.of("Europe/Berlin"), 1024);
        String packet = VALID.replace("100715", date).replace("043602", "023000");
        // when / then
        assertThatThrownBy(() -> regional.decode(frame(packet), RECEIVED)).isInstanceOf(ProtocolException.class);
    }

    @Test
    void decode_configuredLength_acceptsExactLimitAndRejectsOverflow() {
        // given / when / then
        assertThat(new AutoseekerProtocol(ZoneId.of("UTC"), VALID.length()).decode(frame(VALID), RECEIVED)).isNotEmpty();
        assertThatThrownBy(() -> new AutoseekerProtocol(ZoneId.of("UTC"), VALID.length() - 1).decode(frame(VALID), RECEIVED))
                .isInstanceOf(ProtocolException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 65537})
    void construct_invalidLength_rejectsConfiguration(int length) {
        // given / when / then
        assertThatThrownBy(() -> new AutoseekerProtocol(ZoneId.of("UTC"), length)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void decode_compactSouthernWesternCoordinates_preservesDegreesAndMinutes() {
        // given
        String packet = VALID.replace("2234.9273,N,11354.3980,E", "22349273,S,113543980,W");
        // when
        PositionReport report = decode(protocol, packet);
        // then
        assertThat(report.point().latitude()).isCloseTo(-22.5821216667, within(0.000001));
        assertThat(report.point().longitude()).isCloseTo(-113.9066333333, within(0.000001));
    }

    @ParameterizedTest
    @ValueSource(strings = {"9999999999999999999999999999999999999999999999", "300000000000000000000000000000000000000"})
    void decode_finiteSyntaxWithNumericOverflow_rejectsInvalidSpeed(String speed) {
        // given / when / then
        assertThatThrownBy(() -> protocol.decode(frame(VALID.replace("000.06", speed)), RECEIVED))
                .isInstanceOf(ProtocolException.class);
    }

    @Test
    void decode_rejectedFrame_neverEchoesDevicePayloadInMessage() {
        // given
        String packet = VALID.replace("11354.3980", "UNTRUSTED-MARKER");
        // when / then
        assertThatThrownBy(() -> protocol.decode(frame(packet), RECEIVED))
                .isInstanceOf(ProtocolException.class).hasMessageNotContaining("UNTRUSTED-MARKER");
    }

    @Test
    void recognizes_foreignOrIncompleteFrames_isFalse() {
        // given / when / then
        assertThat(protocol.recognizes(frame("*GPS,8168000008,V1,043602,A,2234.9273,N,11354.3980,E,000.06,000,100715#"))).isFalse();
        assertThat(protocol.recognizes(frame("*HQ,8168000008,V1#"))).isFalse();
        assertThat(protocol.recognizes(frame("*XYZ,1234567890,V9,invalid#"))).isFalse();
    }

    @Test
    void encode_fuelCutAndRestore_matchDocumentedWireFormat() {
        // given
        Instant at = Instant.parse("2026-11-13T17:24:05Z");
        // when / then
        assertThat(new String(protocol.commands().encode(DeviceCommand.FUEL_CUT, "8168000008", at), StandardCharsets.US_ASCII))
                .isEqualTo("*HQ,8168000008,S20,172405,1,3,10,3,5,5,3,5,3,5#");
        assertThat(new String(protocol.commands().encode(DeviceCommand.ENGINE_RESTORE, "8168000008", at), StandardCharsets.US_ASCII))
                .isEqualTo("*HQ,8168000008,S20,172405,0,0#");
    }
}
