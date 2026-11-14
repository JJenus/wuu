package com.surf.tracker.core.application.service;

import com.surf.tracker.core.api.dto.PairingTokenResponse;
import com.surf.tracker.core.domain.entity.Tracker;
import com.surf.tracker.shared.security.PairingToken;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Issues the signed credential a phone or browser uses to report as a PHONE tracker. device-comm verifies it
 * with the same secret, so nothing is stored and a leaked token stops working at its expiry.
 */
@Service
public class PhonePairingService {
    public static final String PHONE_PROTOCOL = "PHONE";

    private final Clock clock;
    private final byte[] secret;
    private final Duration ttl;
    private final String publicUrl;

    public PhonePairingService(Clock clock,
                               @Value("${tracking.phone.pairing-secret:}") String secret,
                               @Value("${tracking.phone.pairing-ttl-minutes:720}") long ttlMinutes,
                               @Value("${tracking.phone.public-url:}") String publicUrl) {
        if (ttlMinutes < 1 || ttlMinutes > 7 * 24 * 60) throw new IllegalArgumentException("Pairing TTL must be 1 minute to 7 days");
        this.clock = clock;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.ttl = Duration.ofMinutes(ttlMinutes);
        this.publicUrl = publicUrl == null ? "" : publicUrl.replaceAll("/+$", "");
    }

    public boolean isConfigured() {
        return secret.length >= 32;
    }

    public static boolean isPhone(Tracker tracker) {
        return PHONE_PROTOCOL.equalsIgnoreCase(tracker.getProtocol());
    }

    /** The caller must already have authorized access to {@code tracker} within its tenant. */
    public PairingTokenResponse issue(Tracker tracker) {
        if (!isConfigured()) throw new IllegalStateException("Phone pairing is not configured");
        if (!isPhone(tracker)) throw new IllegalArgumentException("Only trackers with protocol PHONE can be paired");
        Instant expiresAt = clock.instant().plus(ttl);
        String token = PairingToken.issue(secret, tracker.getDeviceId(), expiresAt);
        // The token rides in the URL fragment so it is never sent to a server or written to access logs.
        String url = publicUrl.isEmpty() ? null : publicUrl + "/#t=" + token;
        return new PairingTokenResponse(tracker.getTrackerId(), tracker.getDeviceId(), token, expiresAt, url);
    }
}
