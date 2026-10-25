package com.surf.tracker.userauth.infrastructure.security;

import com.surf.tracker.userauth.application.dto.*;
import com.surf.tracker.userauth.application.service.AuthService;
import com.surf.tracker.userauth.infrastructure.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.annotation.DirtiesContext;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(classes = TokenLifecycleIntegrationTest.Config.class, webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {"spring.config.location=optional:classpath:auth-test-empty.properties",
        "spring.datasource.url=jdbc:h2:mem:auth-lifecycle;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.open-in-view=false",
        "spring.data.redis.repositories.enabled=false", "userauth.registration-enabled=true",
        "userauth.password.bcrypt-strength=4", "userauth.jwt.secret=test-only-secret-with-at-least-thirty-two-bytes"})
@DirtiesContext
@org.springframework.test.context.ActiveProfiles("auth-lifecycle")
class TokenLifecycleIntegrationTest {
    @Configuration
    @org.springframework.context.annotation.Profile("auth-lifecycle")
    @EnableAutoConfiguration
    @EntityScan("com.surf.tracker.userauth.domain.entity")
    @EnableJpaRepositories("com.surf.tracker.userauth.infrastructure.repository")
    @Import({AuthCoreConfig.class, AuthService.class, PasswordService.class, TokenHashService.class, JwtService.class})
    static class Config {}

    private static final AtomicInteger IDS = new AtomicInteger();
    @Autowired AuthService auth;
    @Autowired SessionRepository sessions;
    @Autowired RefreshTokenRepository tokens;
    @Autowired LoginAttemptRepository attempts;
    @Autowired TokenHashService hashes;

    RegisterResponse register() {
        int id = IDS.incrementAndGet();
        RegisterRequest request = new RegisterRequest();
        request.setEmail("lifecycle" + id + "@example.com");
        request.setPassword("a-long-test-password");
        request.setFirstName("Test");
        request.setLastName("User");
        request.setOrganizationName("Lifecycle " + id);
        return auth.register(request, "127.0.0.1", "test");
    }

    @Test void refresh_repeatedRotationAndReplay_revokesCommittedSessionAndDescendants() {
        // given
        var initial = register();
        // when
        var second = auth.refresh(initial.getRefreshToken(), "127.0.0.1", null);
        var third = auth.refresh(second.getRefreshToken(), "127.0.0.1", null);
        assertThatThrownBy(() -> auth.refresh(initial.getRefreshToken(), "127.0.0.1", null))
            .isInstanceOf(SecurityException.class);
        // then: query in a new transaction, proving revocation survived the exception
        assertThat(sessions.findByUserId(initial.getUserId())).allSatisfy(session -> {
            assertThat(session.getRevokedAt()).isNotNull();
            assertThat(tokens.findBySessionId(session.getId())).allSatisfy(token ->
                assertThat(token.getRevokedAt()).isNotNull());
        });
        assertThatThrownBy(() -> auth.refresh(third.getRefreshToken(), "127.0.0.1", null))
            .isInstanceOf(RuntimeException.class);
    }

    @Test void login_wrongPassword_persistsFailureDespiteException() {
        // given
        var initial = register();
        String email = auth.getCurrentUser(initial.getUserId()).getEmail();
        long before = attempts.count();
        // when
        assertThatThrownBy(() -> auth.login(email, "incorrect", "127.0.0.1", null)).isInstanceOf(AuthException.class);
        // then
        assertThat(attempts.count()).isEqualTo(before + 1);
    }

    @Test void refresh_concurrentSameToken_onlyOneSucceedsAndReplayRevokesSession() throws Exception {
        // given
        var initial = register();
        var start = new CountDownLatch(1);
        Callable<Boolean> refresh = () -> {
            start.await();
            try {
                auth.refresh(initial.getRefreshToken(), "127.0.0.1", null);
                return true;
            } catch (SecurityException expected) {
                return false;
            }
        };
        // when
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> first = executor.submit(refresh);
            Future<Boolean> second = executor.submit(refresh);
            start.countDown();
            // then
            assertThat(java.util.List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder(true, false);
        }
        assertThat(sessions.findByUserId(initial.getUserId())).allSatisfy(session ->
            assertThat(session.getRevokedAt()).isNotNull());
    }

    @Test void passwordChange_revokesAllSessions() {
        // given
        var initial = register();
        // when
        auth.changePassword(initial.getUserId(), "a-long-test-password", "a-different-test-password", null, null);
        // then
        assertThat(sessions.findByUserId(initial.getUserId())).allSatisfy(session ->
            assertThat(session.getRevokedAt()).isNotNull());
        assertThatThrownBy(() -> auth.refresh(initial.getRefreshToken(), "127.0.0.1", null))
            .isInstanceOf(RuntimeException.class);
    }

    @Test void login_repeatedWrongPasswords_triggersTemporaryLimitWithoutPermanentLock() {
        // given
        var initial = register();
        String email = auth.getCurrentUser(initial.getUserId()).getEmail();
        // when
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> auth.login(email, "incorrect", "127.0.0.1", null)).isInstanceOf(AuthException.class);
        }
        // then
        assertThatThrownBy(() -> auth.login(email, "a-long-test-password", "127.0.0.1", null)).isInstanceOf(AuthException.class);
        assertThat(auth.getCurrentUser(initial.getUserId()).isLocked()).isFalse();
    }
}
