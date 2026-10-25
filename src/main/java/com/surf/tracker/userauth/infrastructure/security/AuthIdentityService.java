package com.surf.tracker.userauth.infrastructure.security;

import com.surf.tracker.userauth.domain.entity.*;
import com.surf.tracker.userauth.infrastructure.repository.*;
import io.jsonwebtoken.Claims;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** Rehydrates authorization on every request: JWT role claims are never authorization evidence. */
@Service
public class AuthIdentityService {
    private final UserRepository users;
    private final SessionRepository sessions;

    public AuthIdentityService(UserRepository users, SessionRepository sessions) {
        this.users = users;
        this.sessions = sessions;
    }

    @Transactional(readOnly = true)
    public AuthPrincipal authenticate(Claims claims) {
        Long userId = Long.valueOf(claims.getSubject());
        Long orgId = claims.get(JwtService.CLAIM_ORG_ID, Long.class);
        String sessionId = claims.get("sid", String.class);
        if (userId <= 0 || sessionId == null) throw invalid();
        Session session = sessions.findBySessionUuidAndUserId(sessionId, userId).orElseThrow(this::invalid);
        User user = users.findWithAuthoritiesById(userId).orElseThrow(this::invalid);
        if (!user.canAuthenticate() || !session.isActive(Instant.now()) || !Objects.equals(session.getOrgId(), orgId)) {
            throw invalid();
        }
        boolean member = orgId != null && user.getRoles().stream()
            .anyMatch(role -> role.isOrgScoped() && Objects.equals(role.getOrg().getId(), orgId));
        boolean systemAdmin = user.getRoles().stream()
            .anyMatch(role -> role.isSystemRole() && "SUPER_ADMIN".equals(role.getName()));
        if (orgId != null && !member || orgId == null && !systemAdmin) throw invalid();
        Set<String> permissions = new HashSet<>();
        for (Role role : user.getRoles()) {
            if (role.isOrgScoped() && Objects.equals(role.getOrg().getId(), orgId)) {
                role.getPermissions().forEach(permission -> permissions.add(permission.getKey()));
            }
        }
        if (systemAdmin) permissions.add("system.admin");
        return new AuthPrincipal(userId, orgId, sessionId, permissions);
    }

    private BadCredentialsException invalid() { return new BadCredentialsException("Invalid authentication"); }

    @Transactional(readOnly = true)
    public boolean isActive(AuthPrincipal principal) {
        return sessions.findBySessionUuidAndUserId(principal.sessionId(), principal.userId())
            .filter(session -> session.isActive(Instant.now()) && Objects.equals(session.getOrgId(), principal.orgId()))
            .flatMap(session -> users.findWithAuthoritiesById(principal.userId()))
            .filter(User::canAuthenticate)
            .filter(user -> user.getRoles().stream().anyMatch(role -> role.isOrgScoped()
                && Objects.equals(role.getOrg().getId(), principal.orgId())))
            .isPresent();
    }
}
