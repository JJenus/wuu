package com.surf.tracker.core.application.service;

import com.surf.tracker.core.domain.entity.Tracker;
import com.surf.tracker.shared.security.PairingToken;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PhonePairingServiceTest {
    private static final String SECRET = "0123456789abcdef0123456789abcdef";
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-14T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void issue_phoneTracker_returnsTokenDeviceComVerifiesUntilExpiry() {
        // given
        var service = new PhonePairingService(clock, SECRET, 60, "https://track.example.com/");
        // when
        var pairing = service.issue(tracker("PHONE"));
        // then
        assertThat(pairing.expiresAt()).isEqualTo(clock.instant().plusSeconds(3600));
        assertThat(PairingToken.verify(SECRET.getBytes(StandardCharsets.UTF_8), pairing.token(), clock.instant()))
                .contains("2348000000001");
        assertThat(pairing.pairingUrl()).isEqualTo("https://track.example.com/#t=" + pairing.token());
    }

    @Test
    void issue_noPublicUrl_omitsPairingUrl() {
        assertThat(new PhonePairingService(clock, SECRET, 60, "").issue(tracker("phone")).pairingUrl()).isNull();
    }

    @Test
    void issue_nonPhoneTracker_isRefused() {
        assertThatThrownBy(() -> new PhonePairingService(clock, SECRET, 60, "").issue(tracker("GT06")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void issue_noSecretConfigured_isRefusedAndReportedUnconfigured() {
        var service = new PhonePairingService(clock, "", 60, "");
        assertThat(service.isConfigured()).isFalse();
        assertThatThrownBy(() -> service.issue(tracker("PHONE"))).isInstanceOf(IllegalStateException.class);
    }

    private static Tracker tracker(String protocol) {
        var tracker = new Tracker();
        tracker.setTrackerId("TRK-PHONE");
        tracker.setDeviceId("2348000000001");
        tracker.setProtocol(protocol);
        return tracker;
    }
}
