package com.surf.tracker.userauth.infrastructure.security;

import com.surf.tracker.shared.security.TenantContext;
import com.surf.tracker.userauth.api.AuthController;
import com.surf.tracker.userauth.api.UserController;
import com.surf.tracker.userauth.api.OrganizationController;
import com.surf.tracker.userauth.application.service.OrganizationService;
import com.surf.tracker.userauth.application.service.AuthService;
import com.surf.tracker.userauth.domain.entity.*;
import com.surf.tracker.userauth.infrastructure.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringJUnitConfig(SecurityBoundaryIntegrationTest.Config.class)
@WebAppConfiguration
class SecurityBoundaryIntegrationTest {
    @org.springframework.boot.test.context.TestConfiguration
    @EnableWebMvc
    @Import({SecurityConfig.class, LocalAccountsPolicy.class, JwtAuthFilter.class, AuthIdentityService.class,
        AuthController.class, UserController.class, OrganizationController.class, AuthRateLimitFilter.class})
    static class Config {
        @Bean JwtService jwtService() {
            JwtConfig config = new JwtConfig();
            config.setSecret("test-only-secret-with-at-least-thirty-two-bytes");
            return new JwtService(config);
        }
        @Bean UserRepository users() { return mock(UserRepository.class); }
        @Bean SessionRepository sessions() { return mock(SessionRepository.class); }
        @Bean AuthService authService() { return mock(AuthService.class); }
        @Bean OrganizationService organizationService() { return mock(OrganizationService.class); }
    }

    @Autowired WebApplicationContext context;
    @Autowired JwtService jwt;
    @Autowired UserRepository users;
    @Autowired SessionRepository sessions;
    @Autowired AuthService authService;
    @Autowired OrganizationService organizations;
    MockMvc mvc;
    User user;
    Session session;

    @BeforeEach
    void setUp() {
        reset(users, sessions, authService, organizations);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        Organization org = Organization.create("Acme", "acme");
        org.setId(10L);
        user = new User();
        user.setId(1L);
        user.setEmail("a@example.com");
        user.assignRole(Role.orgRole(org, "VIEWER", "viewer"));
        session = Session.create(user, "hash", Instant.now().plusSeconds(600));
        session.setOrgId(10L);
        when(users.findWithAuthoritiesById(1L)).thenReturn(Optional.of(user));
        when(sessions.findBySessionUuidAndUserId(session.getSessionUuid(), 1L)).thenReturn(Optional.of(session));
    }

    String token() {
        return jwt.generateAccessToken(1L, 10L, user.getEmail(), List.of("SUPER_ADMIN"), session.getSessionUuid());
    }

    @Test void request_anonymous_returns401() throws Exception {
        mvc.perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized());
    }

    @Test void roleAssignment_viewerWithForgedRoleClaim_returns403() throws Exception {
        mvc.perform(put("/api/v1/users/1/roles").header("Authorization", "Bearer " + token())
            .contentType("application/json").content("{\"role_ids\":[1]}"))
            .andExpect(status().isForbidden());
        verifyNoInteractions(authService);
    }

    @Test void me_validToken_populatesAndClearsUserContext() throws Exception {
        when(authService.getCurrentUser(1L)).thenAnswer(invocation -> {
            assertThat(TenantContext.getCurrentUserId()).isEqualTo(1L);
            assertThat(TenantContext.getCurrentOrgId()).isEqualTo(10L);
            return new com.surf.tracker.userauth.application.dto.UserResponse();
        });
        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token()))
            .andExpect(status().isOk());
        verify(authService).getCurrentUser(1L);
        assertThat(TenantContext.getCurrentUserId()).isNull();
        assertThat(TenantContext.getCurrentOrgId()).isNull();
    }

    @Test void request_revokedSession_returns401() throws Exception {
        session.revokeAt(Instant.now());
        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token()))
            .andExpect(status().isUnauthorized());
    }

    @Test void request_disabledUser_returns401() throws Exception {
        user.disable();
        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token()))
            .andExpect(status().isUnauthorized());
    }

    @Test void request_unknownRoute_isDenied() throws Exception {
        mvc.perform(get("/unmapped-sensitive-endpoint").header("Authorization", "Bearer " + token()))
            .andExpect(status().isForbidden());
    }

    @Test void request_internalSurfaces_areDenied() throws Exception {
        for (String path : List.of("/h2-console/", "/actuator/metrics", "/api/notifications/webhooks/email/test")) {
            mvc.perform(get(path).header("Authorization", "Bearer " + token()))
                .andExpect(status().isForbidden());
        }
    }

    @Test void request_tenantWithoutPermission_cannotWriteAnyBusinessSurface() throws Exception {
        for (String path : List.of("/api/v1/vehicles", "/api/v1/trackers", "/api/v1/commands",
                "/alerts/rules", "/alerts/geofences", "/alerts/cleanup", "/api/notifications/templates")) {
            mvc.perform(post(path).header("Authorization", "Bearer " + token()).contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        }
    }

    @Test void organizations_tenantReader_cannotReadOtherTenant() throws Exception {
        user.getRoles().iterator().next().addPermission(Permission.of("organizations.read", "read"));
        mvc.perform(get("/api/v1/organizations/20").header("Authorization", "Bearer " + token()))
            .andExpect(status().isForbidden());
        verifyNoInteractions(organizations);
    }

    @Test void organizations_tenantReader_canReadOwnTenant() throws Exception {
        user.getRoles().iterator().next().addPermission(Permission.of("organizations.read", "read"));
        Organization org = Organization.create("Acme", "acme");
        org.setId(10L);
        when(organizations.getById(10L)).thenReturn(org);
        mvc.perform(get("/api/v1/organizations/10").header("Authorization", "Bearer " + token()))
            .andExpect(status().isOk());
    }

    @Test void request_staleTenantMembership_returns401() throws Exception {
        user.clearRoles();
        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token()))
            .andExpect(status().isUnauthorized());
    }

    @Test void cors_untrustedOrigin_isRejected() throws Exception {
        mvc.perform(options("/api/v1/users/me").header("Origin", "https://attacker.example")
                .header("Access-Control-Request-Method", "GET"))
            .andExpect(status().isForbidden());
    }

    @Test void login_spoofedForwardedHeader_doesNotBypassRateLimit() throws Exception {
        for (int i = 0; i < 30; i++) {
            mvc.perform(post("/api/v1/auth/login").with(request -> {
                    request.setRemoteAddr("192.0.2.50"); return request;
                }).header("X-Forwarded-For", "198.51.100." + i)
                .contentType("application/json").content("{\"email\":\"a@example.com\",\"password\":\"test-password\"}"))
                .andExpect(status().isOk());
        }
        mvc.perform(post("/api/v1/auth/login").with(request -> {
                request.setRemoteAddr("192.0.2.50"); return request;
            }).header("X-Forwarded-For", "203.0.113.123").contentType("application/json").content("{}"))
            .andExpect(status().isTooManyRequests());
    }
}
