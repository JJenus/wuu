package com.surf.tracker.userauth.infrastructure.security;

import com.surf.tracker.userauth.domain.entity.Organization;
import com.surf.tracker.userauth.domain.entity.Role;
import com.surf.tracker.userauth.domain.entity.Session;
import com.surf.tracker.userauth.domain.entity.User;
import com.surf.tracker.userauth.infrastructure.repository.SessionRepository;
import com.surf.tracker.userauth.infrastructure.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;

import java.time.Instant;
import java.time.Clock;
import java.util.Date;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class AuthIdentityServiceTest {
    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");
    private final UserRepository users = mock(UserRepository.class);
    private final SessionRepository sessions = mock(SessionRepository.class);
    private final Clock clock = Clock.fixed(NOW, java.time.ZoneOffset.UTC);
    private final AuthIdentityService identities = new AuthIdentityService(users, sessions, clock);

    @Test
    void authenticate_validAccessToken_preservesExpiryForLongLivedConnections() {
        // given
        Claims claims = activeIdentity();
        claims.setExpiration(Date.from(NOW.plusSeconds(60)));
        // when
        AuthPrincipal principal = identities.authenticate(claims);
        // then
        assertThat(principal.accessExpiresAt()).isEqualTo(NOW.plusSeconds(60));
        assertThat(identities.isActive(principal)).isTrue();
    }

    @Test
    void isActive_accessExpiryReached_rejectsEvenBeforeDatabaseLookup() {
        // given
        AuthPrincipal principal = new AuthPrincipal(1L, 10L, "session", Set.of(), NOW);
        // when
        boolean active = identities.isActive(principal);
        // then
        assertThat(active).isFalse();
        verifyNoInteractions(users, sessions);
    }

    @Test
    void authenticate_expiredAccessToken_rejectsActiveDatabaseSession() {
        // given
        Claims claims = activeIdentity();
        claims.setExpiration(Date.from(Instant.EPOCH));
        // when / then
        assertThatThrownBy(() -> identities.authenticate(claims))
            .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void authenticate_missingAccessExpiry_rejectsActiveDatabaseSession() {
        // given
        Claims claims = activeIdentity();
        // when / then
        assertThatThrownBy(() -> identities.authenticate(claims))
            .isInstanceOf(BadCredentialsException.class);
    }

    private Claims activeIdentity() {
        Organization organization = Organization.create("Tenant", "tenant");
        organization.setId(10L);
        User user = new User();
        user.assignRole(Role.orgRole(organization, "TENANT_ADMIN", "Tenant administrator"));
        Session session = Session.create(user, "hash", Instant.parse("2099-01-01T00:00:00Z"));
        session.setOrgId(10L);
        when(sessions.findBySessionUuidAndUserId("session", 1L)).thenReturn(Optional.of(session));
        when(users.findWithAuthoritiesById(1L)).thenReturn(Optional.of(user));
        Claims claims = Jwts.claims();
        claims.setSubject("1");
        claims.put(JwtService.CLAIM_ORG_ID, 10L);
        claims.put("sid", "session");
        return claims;
    }
}
