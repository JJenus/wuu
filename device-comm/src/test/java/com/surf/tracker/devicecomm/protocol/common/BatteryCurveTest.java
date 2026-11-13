package com.surf.tracker.devicecomm.protocol.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BatteryCurveTest {
    private final BatteryCurve curve = new BatteryCurve(1024f, 5.6f, 3.6f);

    @Test
    void fromHex_midRange_convertsToVoltsAndPercent() {
        // given: 0x0200 = 512 -> 2.8 V -> 77.8 %
        // when
        var reading = curve.fromHex("0200").orElseThrow();
        // then
        assertThat(reading.volts()).isEqualTo(2.8f);
        assertThat(reading.percent()).isEqualTo(78);
    }

    @Test
    void fromHex_extremes_clampPercentBetweenOneAndHundred() {
        // given / when / then
        assertThat(curve.fromHex("0000").orElseThrow().percent()).isEqualTo(1);
        assertThat(curve.fromHex("0FFF").orElseThrow().percent()).isEqualTo(100);
    }

    @Test
    void fromHex_notFourHexDigits_isEmpty() {
        // given / when / then
        assertThat(curve.fromHex(null)).isEmpty();
        assertThat(curve.fromHex("123")).isEmpty();
        assertThat(curve.fromHex("12345")).isEmpty();
        assertThat(curve.fromHex("12G4")).isEmpty();
    }

    @Test
    void construct_nonPositiveParameter_rejectsConfiguration() {
        // given / when / then
        assertThatThrownBy(() -> new BatteryCurve(0f, 5.6f, 3.6f)).isInstanceOf(IllegalArgumentException.class);
    }
}
