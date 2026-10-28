package com.surf.tracker.userauth.infrastructure.security;

import java.security.Principal;
import java.time.Instant;
import java.util.Set;

public record AuthPrincipal(Long userId, Long orgId, String sessionId, Set<String> permissions,
                            Instant accessExpiresAt) implements Principal {
    public AuthPrincipal {
        permissions = Set.copyOf(permissions);
    }

    @Override
    public String getName() { return userId.toString(); }
}
