package com.surf.tracker.userauth.infrastructure.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class JwtServiceTest {

    private static final Instant FIXED = Instant.parse("2025-01-01T12:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED, ZoneOffset.UTC);

    private JwtService jwtService;
    private JwtConfig config;

    @BeforeEach
    void setUp() {
        config = new JwtConfig();
        config.setSecret("test-secret-that-is-at-least-32-bytes-long-for-hs256-signing");
        config.setIssuer("test-issuer");
        config.setAccessTokenExpiry(Duration.ofMinutes(15));
        config.setRefreshTokenExpiry(Duration.ofDays(7));
        jwtService = new JwtService(config, FIXED_CLOCK);
    }

    @Test
    void generateAccessToken_returnsJwtWithExpectedClaims() {
        String token = jwtService.generateAccessToken(1L, 2L, "user@example.com", List.of("ADMIN", "USER"));

        assertThat(token).isNotBlank();
        Jws<Claims> parsed = jwtService.parseAndValidate(token);
        Claims claims = parsed.getBody();
        assertThat(claims.getSubject()).isEqualTo("1");
        assertThat(claims.getIssuer()).isEqualTo("test-issuer");
        assertThat(claims.get(JwtService.CLAIM_EMAIL, String.class)).isEqualTo("user@example.com");
        assertThat(claims.get(JwtService.CLAIM_ORG_ID, Long.class)).isEqualTo(2L);
        assertThat(claims.get(JwtService.CLAIM_ROLES, List.class)).containsExactly("ADMIN", "USER");
        assertThat(claims.getExpiration().toInstant()).isEqualTo(FIXED.plus(Duration.ofMinutes(15)));
    }

    @Test
    void parseAndValidate_validToken_succeeds() {
        String token = jwtService.generateAccessToken(10L, 20L, "x@y.com", List.of("VIEWER"));

        Jws<Claims> parsed = jwtService.parseAndValidate(token);

        assertThat(parsed.getBody().getSubject()).isEqualTo("10");
    }

    @Test
    void parseAndValidate_tamperedToken_throws() {
        String token = jwtService.generateAccessToken(1L, 2L, "x@y.com", List.of("ADMIN"));
        String tampered = token.substring(0, token.length() - 4) + "AAAA";

        assertThatThrownBy(() -> jwtService.parseAndValidate(tampered))
            .isInstanceOf(io.jsonwebtoken.JwtException.class);
    }

    @Test
    void parseAndValidate_expiredToken_throws() {
        Clock pastClock = Clock.fixed(FIXED.minus(Duration.ofHours(2)), ZoneOffset.UTC);
        JwtService pastService = new JwtService(config, pastClock);
        String token = pastService.generateAccessToken(1L, 2L, "x@y.com", List.of("ADMIN"));

        assertThatThrownBy(() -> jwtService.parseAndValidate(token))
            .isInstanceOf(io.jsonwebtoken.ExpiredJwtException.class);
    }

    @Test
    void constructor_shortSecret_throws() {
        JwtConfig bad = new JwtConfig();
        bad.setSecret("short");

        assertThatThrownBy(() -> new JwtService(bad, FIXED_CLOCK))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void getAccessTokenExpirySeconds_returnsConfiguredValue() {
        long seconds = jwtService.getAccessTokenExpirySeconds();

        assertThat(seconds).isEqualTo(15 * 60);
    }

    @Test
    void getAccessTokenExpiryAsInstant_isInFuture() {
        Instant expiry = jwtService.getAccessTokenExpiryAsInstant();

        assertThat(expiry).isAfter(FIXED);
    }
}
