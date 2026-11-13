package com.surf.tracker.userauth.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.surf.tracker.main.TrackingEngineApplication;
import com.surf.tracker.userauth.application.dto.LoginResponse;
import com.surf.tracker.userauth.domain.entity.User;
import com.surf.tracker.userauth.domain.entity.Organization;
import com.surf.tracker.userauth.domain.entity.Role;
import com.surf.tracker.userauth.infrastructure.repository.UserRepository;
import com.surf.tracker.userauth.infrastructure.repository.OrganizationRepository;
import com.surf.tracker.userauth.infrastructure.repository.RoleRepository;
import com.surf.tracker.userauth.infrastructure.security.PasswordService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = TrackingEngineApplication.class, properties = {
    "userauth.jwt.secret=test-only-secret-with-at-least-thirty-two-bytes",
    "spring.datasource.url=jdbc:h2:mem:self-account;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
    "spring.jms.listener.auto-startup=false", "userauth.password.bcrypt-strength=4"})
@AutoConfigureMockMvc
class SelfAccountIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordService passwords;
    @Autowired ObjectMapper mapper;
    @Autowired OrganizationRepository organizations;
    @Autowired RoleRepository roles;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    User owner, other;
    LoginResponse session;
    record Credentials(String email, String password) {}
    record Names(String firstName, String lastName) {}
    record PasswordChange(String oldPassword, String newPassword) {}
    record Refresh(String refreshToken) {}

    @BeforeEach void setUp() throws Exception {
        owner = account("self-" + UUID.randomUUID() + "@example.com", "Original");
        other = account("other-" + UUID.randomUUID() + "@example.com", "Untouched");
        session = login(owner.getEmail(), "eight123");
        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + session.getAccessToken())).andExpect(status().isOk());
    }
    @Test void updateMe_authenticatedWithoutAdminPermissions_updatesOnlyOwnNames() throws Exception {
        mvc.perform(put("/api/v1/users/me").header("Authorization", "Bearer " + session.getAccessToken())
            .contentType("application/json").content(mapper.writeValueAsString(new Names("  New  ", "Name"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(owner.getId()))
            .andExpect(jsonPath("$.first_name").value("New")).andExpect(jsonPath("$.last_name").value("Name"))
            .andExpect(jsonPath("$.email").value(owner.getEmail()));
        assertThat(users.findById(other.getId()).orElseThrow().getFirstName()).isEqualTo("Untouched");
        User saved = users.findById(owner.getId()).orElseThrow();
        assertThat(passwords.matches("eight123", saved.getPasswordHash())).isTrue();
        assertThat(saved.isEnabled()).isTrue(); assertThat(saved.isLocked()).isFalse();
        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + session.getAccessToken()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.first_name").value("New"));
    }
    @ParameterizedTest @ValueSource(strings = {"id", "email", "organization_id", "roles", "password", "enabled", "locked"})
    void updateMe_forgedIdentityOrSecurityField_isRejectedWithoutMutation(String field) throws Exception {
        mvc.perform(put("/api/v1/users/me").header("Authorization", "Bearer " + session.getAccessToken())
            .contentType("application/json").content("{\"first_name\":\"Changed\",\"last_name\":\"Name\",\"" + field + "\":\"forged\"}"))
            .andExpect(status().isBadRequest());
        assertThat(users.findById(owner.getId()).orElseThrow().getFirstName()).isEqualTo("Original");
    }
    @Test void updateMe_blankAndOversizedNames_clearsOrRejectsAsDocumented() throws Exception {
        mvc.perform(put("/api/v1/users/me").header("Authorization", "Bearer " + session.getAccessToken())
            .contentType("application/json").content(mapper.writeValueAsString(new Names("", " "))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.first_name").isEmpty()).andExpect(jsonPath("$.last_name").isEmpty());
        mvc.perform(put("/api/v1/users/me").header("Authorization", "Bearer " + session.getAccessToken())
            .contentType("application/json").content(mapper.writeValueAsString(new Names("x".repeat(101), "Name"))))
            .andExpect(status().isBadRequest());
    }
    @Test void updateMe_anonymousAndAdminRoute_requireAuthenticationAndSeparatePermission() throws Exception {
        mvc.perform(put("/api/v1/users/me").contentType("application/json").content("{}"))
            .andExpect(status().isUnauthorized());
        mvc.perform(put("/api/v1/users/" + other.getId()).header("Authorization", "Bearer " + session.getAccessToken())
            .contentType("application/json").content("{}"))
            .andExpect(status().isForbidden());
    }
    @Test void changePassword_wrongOldPassword_keepsSessionsAndPassword() throws Exception {
        mvc.perform(post("/api/v1/auth/change-password").header("Authorization", "Bearer " + session.getAccessToken())
            .contentType("application/json").content(mapper.writeValueAsString(new PasswordChange("wrong", "newpass123"))))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + session.getAccessToken())).andExpect(status().isOk());
        assertThat(passwords.matches("eight123", users.findById(owner.getId()).orElseThrow().getPasswordHash())).isTrue();
    }
    @Test void changePassword_validEightCharacterPassword_revokesAllAccessAndRefreshTokens() throws Exception {
        LoginResponse second = login(owner.getEmail(), "eight123");
        mvc.perform(post("/api/v1/auth/change-password").header("Authorization", "Bearer " + session.getAccessToken())
            .contentType("application/json").content(mapper.writeValueAsString(new PasswordChange("eight123", " newpass "))))
            .andExpect(status().isNoContent());
        for (LoginResponse old : java.util.List.of(session, second)) {
            mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + old.getAccessToken())).andExpect(status().isUnauthorized());
            mvc.perform(post("/api/v1/auth/refresh").contentType("application/json").content(mapper.writeValueAsString(new Refresh(old.getRefreshToken()))))
                .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/v1/auth/login").contentType("application/json").content(mapper.writeValueAsString(new Credentials(owner.getEmail(), "eight123"))))
            .andExpect(status().isUnauthorized());
        assertThat(login(owner.getEmail(), " newpass ").getAccessToken()).isNotBlank();
        assertThat(login(other.getEmail(), "eight123").getAccessToken()).isNotBlank();
    }
    @Test void changePassword_shortOrTooManyUtf8Bytes_rejectsWithoutChangingHash() throws Exception {
        for (String invalid : java.util.List.of("seven12", "é".repeat(37))) {
            mvc.perform(post("/api/v1/auth/change-password").header("Authorization", "Bearer " + session.getAccessToken())
                .contentType("application/json").content(mapper.writeValueAsString(new PasswordChange("eight123", invalid))))
                .andExpect(status().isBadRequest());
        }
        assertThat(passwords.matches("eight123", users.findById(owner.getId()).orElseThrow().getPasswordHash())).isTrue();
    }
    @Test void updateMe_concurrentPasswordSnapshot_doesNotRestoreOldNames() throws Exception {
        var loaded = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        String newHash = passwords.hash("newpass1");
        try (var executor = java.util.concurrent.Executors.newSingleThreadExecutor()) {
            var passwordWrite = executor.submit(() -> {
                new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(status -> {
                    User snapshot = users.findById(owner.getId()).orElseThrow(); loaded.countDown();
                    try {
                        if (!release.await(10, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("Fixture timed out");
                    } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException(interrupted); }
                    snapshot.setPasswordHash(newHash); users.save(snapshot);
                });
                return true;
            });
            assertThat(loaded.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            try {
                mvc.perform(put("/api/v1/users/me").header("Authorization", "Bearer " + session.getAccessToken())
                    .contentType("application/json").content(mapper.writeValueAsString(new Names("Concurrent", "Name"))))
                    .andExpect(status().isOk());
            } finally { release.countDown(); }
            assertThat(passwordWrite.get(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            User saved = users.findById(owner.getId()).orElseThrow();
            assertThat(saved.getFirstName()).isEqualTo("Concurrent");
            assertThat(passwords.matches("newpass1", saved.getPasswordHash())).isTrue();
        } finally { release.countDown(); }
    }
    private User account(String email, String name) {
        return new org.springframework.transaction.support.TransactionTemplate(transactions).execute(status -> {
            var org = organizations.save(Organization.create("Self account", "self-" + UUID.randomUUID()));
            var role = roles.save(Role.orgRole(org, "VIEWER", "No administrative permissions"));
            User user = new User(); user.setEmail(email); user.setFirstName(name); user.setPasswordHash(passwords.hash("eight123"));
            user.assignRole(role); return users.save(user);
        });
    }
    private LoginResponse login(String email, String password) throws Exception {
        return mapper.readValue(mvc.perform(post("/api/v1/auth/login").contentType("application/json")
            .content(mapper.writeValueAsString(new Credentials(email, password))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray(), LoginResponse.class);
    }
}
