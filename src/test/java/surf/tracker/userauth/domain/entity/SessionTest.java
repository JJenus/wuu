package com.surf.tracker.userauth.domain.entity;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class SessionTest {

    private static final Instant FIXED = Instant.parse("2025-01-01T12:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED, ZoneOffset.UTC);

    @Test
    void create_validInput_setsFields() {
        User user = new User();
        user.setId(1L);
        String hash = "abc123";
        Instant expiry = FIXED.plusSeconds(3600);

        Session session = Session.create(user, hash, expiry);

        assertThat(session.getUser()).isEqualTo(user);
        assertThat(session.getTokenHash()).isEqualTo(hash);
        assertThat(session.getExpiresAt()).isEqualTo(expiry);
        assertThat(session.getRevokedAt()).isNull();
        assertThat(session.getSessionUuid()).isNotNull();
    }

    @Test
    void create_nullUser_throws() {
        assertThatThrownBy(() -> Session.create(null, "hash", FIXED.plusSeconds(3600)))
            .isInstanceOf(com.surf.tracker.shared.exception.ValidationException.class);
    }

    @Test
    void create_nullTokenHash_throws() {
        User user = new User();
        assertThatThrownBy(() -> Session.create(user, null, FIXED.plusSeconds(3600)))
            .isInstanceOf(com.surf.tracker.shared.exception.ValidationException.class);
    }

    @Test
    void create_blankTokenHash_throws() {
        User user = new User();
        assertThatThrownBy(() -> Session.create(user, "  ", FIXED.plusSeconds(3600)))
            .isInstanceOf(com.surf.tracker.shared.exception.ValidationException.class);
    }

    @Test
    void create_nullExpiry_throws() {
        User user = new User();
        assertThatThrownBy(() -> Session.create(user, "hash", null))
            .isInstanceOf(com.surf.tracker.shared.exception.ValidationException.class);
    }

    @Test
    void isActive_notExpiredNotRevoked_returnsTrue() {
        User user = new User();
        user.setId(1L);
        Session session = Session.create(user, "hash", FIXED.plusSeconds(3600));

        assertThat(session.isActive(FIXED.plusSeconds(1800))).isTrue();
    }

    @Test
    void isActive_expired_returnsFalse() {
        User user = new User();
        user.setId(1L);
        Session session = Session.create(user, "hash", FIXED.plusSeconds(3600));

        assertThat(session.isActive(FIXED.plusSeconds(7200))).isFalse();
    }

    @Test
    void isActive_revoked_returnsFalse() {
        User user = new User();
        user.setId(1L);
        Session session = Session.create(user, "hash", FIXED.plusSeconds(3600));
        session.revoke();

        assertThat(session.isActive(FIXED.plusSeconds(1800))).isFalse();
    }

    @Test
    void revoke_setsRevokedAt() {
        User user = new User();
        user.setId(1L);
        Session session = Session.create(user, "hash", FIXED.plusSeconds(3600));

        session.revoke();

        assertThat(session.getRevokedAt()).isNotNull();
    }
}
