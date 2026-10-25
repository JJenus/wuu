package com.surf.tracker.userauth.application.service;

import com.surf.tracker.main.TrackingEngineApplication;
import com.surf.tracker.userauth.domain.entity.User;
import com.surf.tracker.userauth.infrastructure.repository.UserRepository;
import com.surf.tracker.userauth.infrastructure.security.PasswordService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.concurrent.CompletableFuture;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = TrackingEngineApplication.class, properties = {
    "userauth.jwt.secret=test-only-secret-with-at-least-thirty-two-bytes",
    "spring.datasource.url=jdbc:h2:mem:local-accounts;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;LOCK_TIMEOUT=10000",
    "spring.jms.listener.auto-startup=false", "server.address=127.0.0.1",
    "userauth.local-accounts.enabled=true", "userauth.password.bcrypt-strength=4",
    "userauth.local-accounts.user-password=test-only-user-password",
    "userauth.local-accounts.admin-password=test-only-admin-password"})
@ActiveProfiles("local")
@AutoConfigureMockMvc
@DirtiesContext
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LocalAccountSeederIntegrationTest {
    @Autowired LocalAccountSeeder seeder;
    @Autowired UserRepository users;
    @Autowired PasswordService passwords;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper mapper;

    @Test void seed_authenticatedUser_canReadAlertsWithoutFalseSessionExpiry() throws Exception {
        // given: the UI queries alerts immediately after a valid login
        String json = mvc.perform(post("/api/v1/auth/login").contentType("application/json")
            .content("{\"email\":\"user@wuu.local\",\"password\":\"test-only-user-password\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String token = mapper.readTree(json).path("access_token").asText();
        // when / then: a valid seeded session must survive the initial workspace reads
        mvc.perform(get("/alerts").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());
    }

    @Test @Order(1) void seed_concurrentEmptyDatabase_createsOneOrganizationAndTwoAccounts() {
        // given: remove only this test database's startup fixtures, before any login creates sessions
        jdbc.update("delete from user_roles");
        jdbc.update("delete from users");
        jdbc.update("delete from role_permissions where role_id in (select id from roles where org_id is not null)");
        jdbc.update("delete from roles where org_id is not null");
        jdbc.update("delete from organizations");
        // when: seed callers race for the same absent users, organization and roles
        CompletableFuture.allOf(CompletableFuture.runAsync(seeder::seed),
            CompletableFuture.runAsync(seeder::seed)).join();
        // then
        assertThat(users.count()).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from organizations", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from roles", Long.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject("select count(*) from user_roles", Long.class)).isEqualTo(2);
    }

    @Test void seed_repeatedAndConcurrentRuns_preserveUsersAndMemberships() {
        // given: startup has already seeded both accounts
        User admin = users.findByEmail("admin@wuu.local").orElseThrow();
        String originalHash = admin.getPasswordHash();
        long originalId = admin.getId();
        long roleCount = jdbc.queryForObject("select count(*) from roles", Long.class);
        jdbc.update("update users set first_name = 'Keep me', enabled = false where id = ?", originalId);
        // when: independent transactions race, as two local instances might
        CompletableFuture.allOf(CompletableFuture.runAsync(seeder::seed),
            CompletableFuture.runAsync(seeder::seed)).join();
        seeder.seed();
        // then: neither hashes, account flags, profile nor relationships are reset
        User unchanged = users.findByEmail("admin@wuu.local").orElseThrow();
        assertThat(unchanged.getId()).isEqualTo(originalId);
        assertThat(unchanged.getPasswordHash()).isEqualTo(originalHash);
        assertThat(unchanged.getFirstName()).isEqualTo("Keep me");
        assertThat(unchanged.isEnabled()).isFalse();
        assertThat(users.count()).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from roles", Long.class)).isEqualTo(roleCount);
        assertThat(jdbc.queryForObject("select count(*) from user_roles", Long.class)).isEqualTo(2);
        jdbc.update("update users set enabled = true where id = ?", originalId);
    }

    @Test
    @org.springframework.transaction.annotation.Transactional
    void seed_existingMixedCaseEmail_isNeverDuplicatedOrElevated() {
        // given: a pre-existing account whose stored email is not normalized
        User admin = users.findByEmail("admin@wuu.local").orElseThrow();
        admin.setEmail("ADMIN@WUU.LOCAL");
        users.saveAndFlush(admin);
        // when
        seeder.seed();
        // then: logical identity takes precedence over case-sensitive database uniqueness
        assertThat(users.count()).isEqualTo(2);
        assertThat(users.findByEmail("admin@wuu.local")).isEmpty();
    }

    @Test void seed_passwordAuthenticationAndLeastPrivilege_arePreserved() throws Exception {
        // given
        User user = users.findByEmail("user@wuu.local").orElseThrow();
        assertThat(passwords.matches("test-only-user-password", user.getPasswordHash())).isTrue();
        assertThat(user.getPasswordHash()).isNotEqualTo("test-only-user-password");
        assertThat(jdbc.queryForObject("select r.name from roles r join user_roles ur on ur.role_id=r.id where ur.user_id=?",
            String.class, user.getId())).isEqualTo("VIEWER");
        // when / then: selecting an email is not sufficient to authenticate
        mvc.perform(post("/api/v1/auth/login").contentType("application/json")
            .content("{\"email\":\"user@wuu.local\",\"password\":\"wrong-password\"}"))
            .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login").contentType("application/json")
            .content("{\"email\":\"user@wuu.local\",\"password\":\"test-only-user-password\"}"))
            .andExpect(status().isOk());
    }

    @Test void accounts_backendQueryIncludesNewUsersButExcludesUnavailableAccounts() throws Exception {
        // given: accounts are not a hardcoded seed roster
        User added = new User();
        added.setEmail("additional@example.test");
        added.setFirstName("Additional");
        added.setPasswordHash(passwords.hash("test-only-added-password"));
        users.saveAndFlush(added);
        User locked = new User();
        locked.setEmail("locked@example.test");
        locked.lock();
        users.saveAndFlush(locked);
        // when / then: only minimal identity is public in explicitly local mode
        mvc.perform(get("/api/v1/auth/local-accounts"))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.accounts[0].email").value("additional@example.test"))
            .andExpect(jsonPath("$.accounts.length()").value(3))
            .andExpect(jsonPath("$.accounts[0].password_hash").doesNotExist())
            .andExpect(jsonPath("$.accounts[0].roles").doesNotExist());
        mvc.perform(get("/api/v1/auth/local-accounts").with(r -> { r.setRemoteAddr("192.0.2.1"); return r; }))
            .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/auth/local-accounts?page=-1"))
            .andExpect(status().isBadRequest());
        users.delete(added);
        users.delete(locked);
    }
}
