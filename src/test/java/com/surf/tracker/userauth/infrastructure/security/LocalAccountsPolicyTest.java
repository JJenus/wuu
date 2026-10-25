package com.surf.tracker.userauth.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.*;

class LocalAccountsPolicyTest {
    @Test void localAccounts_disabledByDefault_doNotExposeAccounts() {
        assertThat(new LocalAccountsPolicy(new MockEnvironment()).isEnabled()).isFalse();
    }

    @Test void localAccounts_explicitLocalProfileAndLoopback_areRequired() {
        MockEnvironment env = new MockEnvironment()
            .withProperty("userauth.local-accounts.enabled", "true")
            .withProperty("server.address", "127.0.0.1");
        assertThatThrownBy(() -> new LocalAccountsPolicy(env)).isInstanceOf(IllegalStateException.class);
        env.setActiveProfiles("local");
        assertThat(new LocalAccountsPolicy(env).isEnabled()).isTrue();
        env.setProperty("server.address", "0.0.0.0");
        assertThatThrownBy(() -> new LocalAccountsPolicy(env)).isInstanceOf(IllegalStateException.class);
        env.setProperty("server.address", "127.0.0.1");
        env.setActiveProfiles("local", "production");
        assertThatThrownBy(() -> new LocalAccountsPolicy(env)).isInstanceOf(IllegalStateException.class);
        env.setActiveProfiles("local", "prod");
        assertThatThrownBy(() -> new LocalAccountsPolicy(env)).isInstanceOf(IllegalStateException.class);
    }
}
