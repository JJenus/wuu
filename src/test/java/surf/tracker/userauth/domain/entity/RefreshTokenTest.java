package com.surf.tracker.userauth.domain.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.Clock;

import static org.assertj.core.api.Assertions.*;

class RefreshTokenTest {

    private static final Instant FIXED = Instant.parse("2025-01-01T12:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED, ZoneOffset.UTC);

    @Test
    void issue_validInput_createsToken() {
        User user = new User();
        user.setId(1L);
        Session session = Session.create(user, "hash", FIXED.plusSeconds(3600));

        RefreshToken rt = RefreshToken.issue(session, "tokHash", FIXED.plusSeconds(604800));

        assertThat(rt.getSession()).isEqualTo(session);
        assertThat(rt.getTokenHash()).isEqualTo("tokHash");
        assertThat(rt.getRotatedFrom()).isNull();
        assertThat(rt.getRevokedAt()).isNull();
        assertThat(rt.hasBeenRotated()).isFalse();
    }

    @Test
    void issue_nullSession_throws() {
        assertThatThrownBy(() -> RefreshToken.issue(null, "hash", FIXED.plusSeconds(3600)))
            .isInstanceOf(com.surf.tracker.shared.exception.ValidationException.class);
    }

    @Test
    void issue_nullHash_throws() {
        User user = new User();
        user.setId(1L);
        Session session = Session.create(user, "hash", FIXED.plusSeconds(3600));

        assertThatThrownBy(() -> RefreshToken.issue(session, null, FIXED.plusSeconds(3600)))
            .isInstanceOf(com.surf.tracker.shared.exception.ValidationException.class);
    }

    @Test
    void rotateFrom_setsRotatedFrom() {
        User user = new User();
        user.setId(1L);
        Session session = Session.create(user, "hash", FIXED.plusSeconds(3600));
        RefreshToken old = RefreshToken.issue(session, "oldHash", FIXED.plusSeconds(3600));
        old.setId(10L);

        RefreshToken next = RefreshToken.rotateFrom(session, old, "newHash", FIXED.plusSeconds(7200));

        assertThat(next.getRotatedFrom()).isEqualTo(old);
        assertThat(next.hasBeenRotated()).isFalse();
        assertThat(next.getTokenHash()).isEqualTo("newHash");
    }

    @Test
    void isActive_notExpiredNotRevoked_returnsTrue() {
        User user = new User();
        user.setId(1L);
        Session session = Session.create(user, "hash", FIXED.plusSeconds(3600));
        RefreshToken rt = RefreshToken.issue(session, "hash", FIXED.plusSeconds(604800));

        assertThat(rt.isActive(FIXED.plusSeconds(3600))).isTrue();
    }

    @Test
    void isActive_expired_returnsFalse() {
        User user = new User();
        user.setId(1L);
        Session session = Session.create(user, "hash", FIXED.plusSeconds(3600));
        RefreshToken rt = RefreshToken.issue(session, "hash", FIXED.plusSeconds(3600));

        assertThat(rt.isActive(FIXED.plusSeconds(7200))).isFalse();
    }

    @Test
    void isActive_revoked_returnsFalse() {
        User user = new User();
        user.setId(1L);
        Session session = Session.create(user, "hash", FIXED.plusSeconds(3600));
        RefreshToken rt = RefreshToken.issue(session, "hash", FIXED.plusSeconds(604800));
        rt.revoke();

        assertThat(rt.isActive(FIXED.plusSeconds(3600))).isFalse();
    }

    @Test
    void revoke_setsRevokedAt() {
        User user = new User();
        user.setId(1L);
        Session session = Session.create(user, "hash", FIXED.plusSeconds(3600));
        RefreshToken rt = RefreshToken.issue(session, "hash", FIXED.plusSeconds(604800));

        rt.revoke();

        assertThat(rt.getRevokedAt()).isNotNull();
    }
}
