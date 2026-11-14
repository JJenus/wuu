package com.surf.tracker.shared.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

/**
 * Stateless, signed proof that a transport peer may speak as one device until an expiry time.
 * Core issues it for a tracker an authorized user owns; device-comm verifies it with the same secret,
 * so the two modules need no shared storage. Format: {@code v1.<deviceId>.<expiryEpochSeconds>.<signature>}.
 */
public final class PairingToken {
    private static final int MIN_SECRET_BYTES = 32;
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private PairingToken() { }

    public static String issue(byte[] secret, String deviceId, Instant expiresAt) {
        requireSecret(secret);
        requireDeviceId(deviceId);
        long expiry = expiresAt.getEpochSecond();
        return "v1." + deviceId + "." + expiry + "." + sign(secret, deviceId, expiry);
    }

    /** The device id the token proves, or empty if it is malformed, expired or not signed with {@code secret}. */
    public static Optional<String> verify(byte[] secret, String token, Instant now) {
        requireSecret(secret);
        if (token == null || token.length() > 200) return Optional.empty();
        String[] parts = token.split("\\.", -1);
        if (parts.length != 4 || !parts[0].equals("v1") || !parts[1].matches("[0-9]{10,15}")
                || !parts[2].matches("[0-9]{1,12}")) return Optional.empty();
        long expiry = Long.parseLong(parts[2]);
        byte[] expected = sign(secret, parts[1], expiry).getBytes(StandardCharsets.US_ASCII);
        byte[] presented = parts[3].getBytes(StandardCharsets.US_ASCII);
        // Constant-time comparison; check expiry only after the signature so the response never leaks which failed.
        boolean signed = MessageDigest.isEqual(expected, presented);
        return signed && now.getEpochSecond() < expiry ? Optional.of(parts[1]) : Optional.empty();
    }

    private static String sign(byte[] secret, String deviceId, long expiry) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return ENCODER.encodeToString(mac.doFinal(
                    ("phone-pairing:v1:" + deviceId + ":" + expiry).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException impossible) {
            throw new IllegalStateException("HmacSHA256 unavailable", impossible);
        }
    }

    private static void requireSecret(byte[] secret) {
        if (secret == null || secret.length < MIN_SECRET_BYTES)
            throw new IllegalArgumentException("Pairing secret must be at least " + MIN_SECRET_BYTES + " bytes");
    }

    private static void requireDeviceId(String deviceId) {
        if (deviceId == null || !deviceId.matches("[0-9]{10,15}"))
            throw new IllegalArgumentException("Pairing requires a 10-15 digit device id");
    }
}
