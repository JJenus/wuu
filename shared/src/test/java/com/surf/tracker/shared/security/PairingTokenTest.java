package com.surf.tracker.shared.security;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PairingTokenTest {
    private static final byte[] SECRET = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);
    private static final Instant NOW = Instant.parse("2026-11-14T12:00:00Z");

    @Test
    void verify_freshToken_returnsTheDeviceItWasIssuedFor() {
        var token = PairingToken.issue(SECRET, "2348000000001", NOW.plusSeconds(60));
        assertThat(PairingToken.verify(SECRET, token, NOW)).contains("2348000000001");
    }

    @Test
    void verify_expiredToken_isRejected() {
        var token = PairingToken.issue(SECRET, "2348000000001", NOW.plusSeconds(60));
        assertThat(PairingToken.verify(SECRET, token, NOW.plusSeconds(60))).isEmpty();
    }

    @Test
    void verify_deviceIdSwappedInToken_isRejected() {
        var token = PairingToken.issue(SECRET, "2348000000001", NOW.plusSeconds(60));
        assertThat(PairingToken.verify(SECRET, token.replace("2348000000001", "2348000000002"), NOW)).isEmpty();
    }

    @Test
    void verify_expiryExtended_isRejected() {
        var token = PairingToken.issue(SECRET, "2348000000001", NOW.plusSeconds(60));
        var parts = token.split("\\.");
        var forged = parts[0] + "." + parts[1] + "." + (NOW.getEpochSecond() + 99999) + "." + parts[3];
        assertThat(PairingToken.verify(SECRET, forged, NOW)).isEmpty();
    }

    @Test
    void verify_otherSecret_isRejected() {
        var token = PairingToken.issue(SECRET, "2348000000001", NOW.plusSeconds(60));
        var other = "ffffffffffffffffffffffffffffffff".getBytes(StandardCharsets.UTF_8);
        assertThat(PairingToken.verify(other, token, NOW)).isEmpty();
    }

    @Test
    void verify_malformedInput_isRejectedWithoutThrowing() {
        for (String junk : new String[]{null, "", "v1", "v1....", "v2.2348000000001.9999999999.x", "a.b.c.d", "x".repeat(500)})
            assertThat(PairingToken.verify(SECRET, junk, NOW)).as(String.valueOf(junk)).isEmpty();
    }

    @Test
    void issue_weakSecretOrBadDevice_isRefused() {
        assertThatThrownBy(() -> PairingToken.issue("short".getBytes(StandardCharsets.UTF_8), "2348000000001", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PairingToken.issue(SECRET, "DEV-001", NOW)).isInstanceOf(IllegalArgumentException.class);
    }
}
