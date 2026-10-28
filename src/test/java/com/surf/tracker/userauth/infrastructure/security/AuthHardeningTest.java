package com.surf.tracker.userauth.infrastructure.security;

import com.surf.tracker.shared.security.TenantContext;
import com.surf.tracker.userauth.domain.entity.RefreshToken;
import com.surf.tracker.userauth.domain.entity.Session;
import com.surf.tracker.userauth.domain.entity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class AuthHardeningTest {
    @AfterEach
    void cleanUp() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void rotation_freshSuccessor_isNotAlreadyConsumed() {
        // given
        Session session = Session.create(new User(), "hash", Instant.now().plusSeconds(600));
        RefreshToken first = RefreshToken.issue(session, "first", session.getExpiresAt());
        // when
        RefreshToken successor = RefreshToken.rotateFrom(session, first, "second", session.getExpiresAt());
        // then
        assertThat(successor.hasBeenRotated()).isFalse();
    }

    @Test
    void parseAndValidate_foreignIssuer_isRejected() {
        // given
        JwtConfig config = config();
        config.setIssuer("foreign-service");
        String token = new JwtService(config).generateAccessToken(1L, 10L, "a@example.com", List.of());
        // when / then
        assertThatThrownBy(() -> new JwtService(config()).parseAndValidate(token))
            .isInstanceOf(io.jsonwebtoken.JwtException.class);
    }

    @Test
    void password_exceedsBcryptByteLimit_isRejected() {
        // given
        PasswordService passwords = new PasswordService(4);
        // when / then
        assertThatThrownBy(() -> passwords.hash("x".repeat(73)))
            .isInstanceOf(com.surf.tracker.shared.exception.ValidationException.class);
    }

    private JwtConfig config() {
        JwtConfig config = new JwtConfig();
        config.setSecret("test-only-secret-with-at-least-thirty-two-bytes");
        return config;
    }
}
