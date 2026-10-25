package com.surf.tracker.userauth.infrastructure.security;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/** A pre-login directory is suitable only for an explicitly opted-in, loopback local database. */
@Component
public class LocalAccountsPolicy {
    private final boolean enabled;

    public LocalAccountsPolicy(Environment environment) {
        enabled = environment.getProperty("userauth.local-accounts.enabled", Boolean.class, false);
        if (enabled && (!environment.acceptsProfiles(Profiles.of("local & !prod & !production"))
                || !isLoopback(environment.getProperty("server.address", "")))) {
            throw new IllegalStateException("Local accounts require the local-only profile and a literal loopback server.address");
        }
    }

    public boolean isEnabled() { return enabled; }

    public static boolean isLoopback(String address) {
        return "127.0.0.1".equals(address) || "::1".equals(address)
            || "0:0:0:0:0:0:0:1".equals(address) || "::ffff:127.0.0.1".equals(address);
    }
}
