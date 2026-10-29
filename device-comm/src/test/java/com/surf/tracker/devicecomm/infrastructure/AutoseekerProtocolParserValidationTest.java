package com.surf.tracker.devicecomm.infrastructure;

import com.surf.tracker.devicecomm.exception.ProtocolParseException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.time.ZoneId;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.*;

class AutoseekerProtocolParserValidationTest {
    private final AutoseekerProtocolParser parser = new AutoseekerProtocolParser();
    private static final String VALID = "*HQ,8168000008,V1,043602,A,2234.9273,N,11354.3980,E,000.06,000,100715,FBFBBFF,460,00,10342,4283,10,25,128#";

    @ParameterizedTest
    @CsvSource({"5,garbage", "5,NaN", "5,Infinity", "5,-2234.9273", "5,2260.0000",
        "5,9000.0001", "7,18000.0001", "7,18100.0000", "7,11360.0000", "6,E", "8,N",
        "9,-1", "9,NaN", "9,Infinity", "9,1e40", "10,360", "10,-1", "10,NaN",
        "3,246000", "11,310226", "11,000000", "4,X", "4,V"})
    void parse_malformedTelemetry_rejectsInsteadOfInventingPosition(int index, String value) {
        // given
        String[] fields = VALID.substring(0, VALID.length() - 1).split(",", -1);
        fields[index] = value;
        String packet = String.join(",", fields) + "#";
        // when / then
        assertThatThrownBy(() -> parser.parse(packet)).isInstanceOf(ProtocolParseException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"\u0000", "\u001b", "é", "#extra", "\r", "\n"})
    void parse_controlNonAsciiOrEmbeddedDelimiter_rejectsFrame(String injected) {
        // given
        String packet = VALID.replace("FBFBBFF", "FBF" + injected + "BBFF");
        // when / then
        assertThatThrownBy(() -> parser.parse(packet)).isInstanceOf(ProtocolParseException.class);
    }

    @Test
    void parse_oversizedFrame_rejectsBeforeSplitting() {
        // given
        String packet = VALID.replace("FBFBBFF", "F".repeat(2000));
        // when / then
        assertThatThrownBy(() -> parser.parse(packet)).isInstanceOf(ProtocolParseException.class);
    }

    @Test
    void parse_validFrame_usesUtcIndependentOfHostTimezone() throws Exception {
        // given
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Singapore"));
            // when
            var location = parser.parse(VALID);
            // then
            assertThat(location.timestamp()).isEqualTo(Instant.parse("2015-07-10T04:36:02Z"));
            assertThat(location.isValid()).isTrue();
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    void parse_polesAndDateLine_acceptsExactBoundaries() throws Exception {
        // given
        String packet = VALID.replace("2234.9273,N,11354.3980,E", "9000.0000,S,18000.0000,W");
        // when
        var location = parser.parse(packet);
        // then
        assertThat(location.latitude()).isEqualTo(-90);
        assertThat(location.longitude()).isEqualTo(-180);
        assertThat(location.isValid()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "*HQ,8168000008,V4,D1,30,65535,043600,043602,A,2234.9273,N,11354.3980,E,000.06,000,100715,FFFFBBFF,460,00,10342,3721#",
        "*HQ,8168000008,V4,S20,DONE,043600,043602,A,2234.9273,N,11354.3980,E,000.06,000,100715F7FFBBFF,460,00,10342,3721#",
        "*HQ,8168000008,V4,SCF,043600,043602,A,2234.9273,N,11354.3980,E,000.06,000,100715,FFFFB9FF,460,00,10342,3721#"
    })
    void parse_documentedCommandResponses_preservesActualGpsFix(String packet) throws Exception {
        // given: formats already emitted by the repository's Autoseeker simulator.
        // when
        var location = parser.parse(packet);
        // then
        assertThat(location.timestamp()).isEqualTo(Instant.parse("2015-07-10T04:36:02Z"));
        assertThat(location.latitude()).isCloseTo(22.5821216667, within(0.000001));
        assertThat(location.longitude()).isCloseTo(113.9066333333, within(0.000001));
        assertThat(location.isValid()).isTrue();
    }

    @Test
    void parse_configuredZone_usesExplicitOffset() throws Exception {
        // given / when
        var location = new AutoseekerProtocolParser(ZoneId.of("Asia/Singapore"), 1024).parse(VALID);
        // then
        assertThat(location.timestamp()).isEqualTo(Instant.parse("2015-07-09T20:36:02Z"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"290326", "251026"})
    void parse_nonexistentOrAmbiguousLocalTime_rejectsInsteadOfGuessing(String date) {
        // given
        var regional = new AutoseekerProtocolParser(ZoneId.of("Europe/Berlin"), 1024);
        String packet = VALID.replace("100715", date).replace("043602", "023000");
        // when / then
        assertThatThrownBy(() -> regional.parse(packet)).isInstanceOf(ProtocolParseException.class);
    }

    @Test
    void parse_configuredLength_acceptsExactLimitAndRejectsOverflow() throws Exception {
        // given / when / then
        assertThat(new AutoseekerProtocolParser(ZoneId.of("UTC"), VALID.length()).parse(VALID)).isNotNull();
        assertThatThrownBy(() -> new AutoseekerProtocolParser(ZoneId.of("UTC"), VALID.length() - 1).parse(VALID))
            .isInstanceOf(ProtocolParseException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 65537})
    void construct_invalidLength_rejectsConfiguration(int length) {
        // given / when / then
        assertThatThrownBy(() -> new AutoseekerProtocolParser(ZoneId.of("UTC"), length))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parse_compactSouthernWesternCoordinates_preservesDegreesAndMinutes() throws Exception {
        // given
        String packet = VALID.replace("2234.9273,N,11354.3980,E", "22349273,S,113543980,W");
        // when
        var location = parser.parse(packet);
        // then
        assertThat(location.latitude()).isCloseTo(-22.5821216667, within(0.000001));
        assertThat(location.longitude()).isCloseTo(-113.9066333333, within(0.000001));
    }

    @ParameterizedTest
    @ValueSource(strings = {"9999999999999999999999999999999999999999999999", "300000000000000000000000000000000000000"})
    void parse_finiteSyntaxWithNumericOverflow_rejectsInvalidSpeed(String speed) {
        // given / when / then
        assertThatThrownBy(() -> parser.parse(VALID.replace("000.06", speed)))
            .isInstanceOf(ProtocolParseException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "*HQ,8168000008,V4,D1,30,65535,043600,043602,A,NaN,N,11354.3980,E,000.06,000,100715#",
        "*HQ,8168000008,V4,S20,DONE,043600,043602,A,2234.9273,N,11354.3980,E,000.06,000,310226F7FFBBFF#",
        "*HQ,8168000008,V4,SCF,043600,043602,A,2234.9273,N,11354.3980,E,000.06,000#"
    })
    void parse_malformedCommandResponse_rejectsWithoutSyntheticFix(String packet) {
        // given / when / then
        assertThatThrownBy(() -> parser.parse(packet)).isInstanceOf(ProtocolParseException.class);
    }
}
