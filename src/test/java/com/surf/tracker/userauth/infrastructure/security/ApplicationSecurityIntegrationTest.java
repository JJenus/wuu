package com.surf.tracker.userauth.infrastructure.security;

import com.surf.tracker.main.TrackingEngineApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import com.surf.tracker.userauth.domain.entity.*;
import com.surf.tracker.userauth.infrastructure.repository.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Starts the composed application, not a standalone mocked controller. */
@SpringBootTest(classes = TrackingEngineApplication.class, properties = {
    "userauth.jwt.secret=test-only-secret-with-at-least-thirty-two-bytes",
    "spring.datasource.url=jdbc:h2:mem:auth-app;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
    "spring.jms.listener.auto-startup=false", "spring.main.lazy-initialization=false"})
@AutoConfigureMockMvc
@DirtiesContext
class ApplicationSecurityIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired OrganizationRepository organizations;
    @Autowired RoleRepository roles;
    @Autowired PasswordService passwords;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper mapper;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;

    @Test void application_anonymousSensitiveRoutes_areProtected() throws Exception {
        mvc.perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/register").contentType("application/json").content("{}"))
            .andExpect(status().isUnauthorized());
        mvc.perform(get("/h2-console/")).andExpect(status().isUnauthorized());
    }

    @Test void application_realLoginMeAndLogout_invalidatesOldAccessToken() throws Exception {
        // given: out-of-band fixture provisioning; public registration remains disabled
        new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Organization org = organizations.save(Organization.create("HTTP auth", "http-auth"));
            Role role = roles.save(Role.orgRole(org, "VIEWER", "viewer"));
            User user = new User();
            user.setEmail("http-auth@example.com");
            user.setPasswordHash(passwords.hash("a-long-test-password"));
            user.assignRole(role);
            users.save(user);
        });
        // when
        String json = mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                .content("{\"email\":\"http-auth@example.com\",\"password\":\"a-long-test-password\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String token = mapper.readTree(json).path("access_token").asText();
        org.assertj.core.api.Assertions.assertThat(token).isNotBlank();
        org.assertj.core.api.Assertions.assertThat(json).doesNotContain("@class");
        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + token))
            .andExpect(status().isNoContent());
        // then
        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
            .andExpect(status().isUnauthorized());
    }
}
