package com.surf.tracker.devicecomm.protocol.common;

import com.surf.tracker.shared.domain.DeviceAlarm;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static com.surf.tracker.devicecomm.protocol.common.StatusBitMap.bit;
import static org.assertj.core.api.Assertions.assertThat;

class StatusBitMapTest {
    @Test
    void decode_activeLowMap_reportsClearedBitsOnly() {
        // given
        var map = StatusBitMap.activeLow(bit(0, DeviceAlarm.SOS), bit(9, DeviceAlarm.TAMPER));
        // when / then: 0xFFFFFDFE clears bits 0 and 9
        assertThat(map.decode("FFFFFDFE")).isEqualTo(EnumSet.of(DeviceAlarm.SOS, DeviceAlarm.TAMPER));
        assertThat(map.decode("FFFFFFFF")).isEmpty();
    }

    @Test
    void decode_activeHighMap_reportsSetBitsOnly() {
        // given
        var map = StatusBitMap.activeHigh(bit(1, DeviceAlarm.VIBRATION));
        // when / then
        assertThat(map.decode("2")).containsExactly(DeviceAlarm.VIBRATION);
        assertThat(map.decode("0")).isEmpty();
    }

    @Test
    void decode_missingOrMalformedWord_reportsNoAlarmsInsteadOfGuessing() {
        // given
        var map = StatusBitMap.activeLow(bit(0, DeviceAlarm.SOS));
        // when / then
        assertThat(map.decode(null)).isEmpty();
        assertThat(map.decode("")).isEmpty();
        assertThat(map.decode("ZZ")).isEmpty();
        assertThat(map.decode("123456789")).isEmpty();
    }
}
