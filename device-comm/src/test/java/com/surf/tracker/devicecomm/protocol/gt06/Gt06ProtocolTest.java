package com.surf.tracker.devicecomm.protocol.gt06;

import com.surf.tracker.devicecomm.exception.ProtocolException;
import com.surf.tracker.devicecomm.protocol.DeviceCommand;
import com.surf.tracker.devicecomm.protocol.Frame;
import com.surf.tracker.devicecomm.protocol.model.DeviceAlarm;
import com.surf.tracker.devicecomm.protocol.model.DeviceMessage;
import com.surf.tracker.devicecomm.protocol.model.LoginReport;
import com.surf.tracker.devicecomm.protocol.model.PositionReport;
import com.surf.tracker.devicecomm.protocol.model.StatusReport;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Gt06ProtocolTest {
    private static final String POSITION = "*HQ,865205030330012,V1,145452,A,2240.55181,N,11358.32389,E,0.00,0,100815,FFFFFBFF#";
    private static final Instant RECEIVED = Instant.parse("2026-11-13T00:00:00Z");
    private final Gt06Protocol protocol = new Gt06Protocol();

    private static Frame frame(String text) {
        return new Frame(text.getBytes(StandardCharsets.UTF_8));
    }

    private DeviceMessage decode(String text) {
        return protocol.decode(frame(text), RECEIVED).getFirst();
    }

    @Test
    void decode_positionFrame_returnsFixWithUtcTimestampAndNoAlarms() {
        // given / when
        PositionReport report = (PositionReport) decode(POSITION);
        // then
        assertThat(report.deviceId()).isEqualTo("865205030330012");
        assertThat(report.point().timestamp()).isEqualTo(Instant.parse("2015-08-10T14:54:52Z"));
        assertThat(report.point().isValid()).isTrue();
        assertThat(report.health().alarms()).isEmpty();
    }

    @Test
    void decode_statusWordWithSosBitCleared_reportsSos() {
        // given
        String frame = POSITION.replace("FFFFFBFF", "FFFFFFFE");
        // when / then
        assertThat(((PositionReport) decode(frame)).health().alarms()).isEqualTo(EnumSet.of(DeviceAlarm.SOS));
    }

    @Test
    void decode_invalidFix_isRejectedInsteadOfPublishingLastKnownCoordinates() {
        // given / when / then
        assertThatThrownBy(() -> decode(POSITION.replace(",A,", ",V,"))).isInstanceOf(ProtocolException.class);
    }

    @Test
    void decode_corruptCoordinate_isRejectedInsteadOfBecomingZero() {
        // given / when / then
        assertThatThrownBy(() -> decode(POSITION.replace("2240.55181", "garbage"))).isInstanceOf(ProtocolException.class);
    }

    @Test
    void decode_corruptDate_isRejectedInsteadOfUsingCurrentTime() {
        // given / when / then
        assertThatThrownBy(() -> decode(POSITION.replace("100815", "991399"))).isInstanceOf(ProtocolException.class);
    }

    @Test
    void decode_loginFrame_returnsLoginReport() {
        // given / when / then
        assertThat(decode("*HQ,865205030330012,V0#")).isInstanceOf(LoginReport.class);
    }

    @Test
    void decode_bareHeartbeat_returnsStatusReportWithoutLocation() {
        // given / when
        DeviceMessage message = decode("*HQ,865205030330012,HTBT#");
        // then
        assertThat(message).isInstanceOf(StatusReport.class);
        assertThat(((StatusReport) message).health().batteryPercent()).isNull();
    }

    @Test
    void decode_heartbeatWithBattery_reportsPercent() {
        // given / when / then
        assertThat(((StatusReport) decode("*HQ,865205030330012,HTBT,87#")).health().batteryPercent()).isEqualTo(87);
    }

    @Test
    void decode_lbsFrame_reportsBatteryButNoLocation() {
        // given: 0x0300 = 768 -> 4.2 V -> clamped to 100 %
        String frame = "*HQ,865205030330012,V3,000201,46000,07,0300,0001,0F00,100815,FFFFFBFF#";
        // when
        StatusReport report = (StatusReport) decode(frame);
        // then
        assertThat(report.health().batteryPercent()).isEqualTo(100);
        assertThat(report.health().batteryVolts()).isEqualTo(4.2f);
    }

    @Test
    void decode_commandResponseWithCorruptedDateSuffix_usesLeadingDate() {
        // given
        String frame = "*HQ,8168000005,V4,S20,DONE,061158,061116,A,2235.0086,N,11354.3668,E,000.00,000,160716F7FFBBFF,460,00#";
        // when
        PositionReport report = (PositionReport) decode(frame);
        // then
        assertThat(report.point().timestamp()).isEqualTo(Instant.parse("2016-07-16T06:11:16Z"));
    }

    @Test
    void recognizes_tenDigitAndLongerIds_isTrueForBoth() {
        // given / when / then
        assertThat(protocol.recognizes(frame(POSITION))).isTrue();
        assertThat(protocol.recognizes(frame(POSITION.replace("865205030330012", "8168000008")))).isTrue();
    }

    @Test
    void encode_fuelCutAndRestore_matchDocumentedWireFormat() {
        // given
        Instant at = Instant.parse("2026-11-13T17:24:05Z");
        // when / then
        assertThat(new String(protocol.commands().encode(DeviceCommand.FUEL_CUT, "865205030330012", at), StandardCharsets.US_ASCII))
                .isEqualTo("*HQ,865205030330012,S20,172405,1,3#");
        assertThat(new String(protocol.commands().encode(DeviceCommand.ENGINE_RESTORE, "865205030330012", at), StandardCharsets.US_ASCII))
                .isEqualTo("*HQ,865205030330012,S20,172405,1,0#");
    }
}
