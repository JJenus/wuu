package com.surf.tracker.userauth.api;

import com.surf.tracker.shared.security.TenantContext;
import com.surf.tracker.userauth.application.service.AuthService;
import com.surf.tracker.userauth.domain.entity.*;
import com.surf.tracker.userauth.infrastructure.repository.*;
import com.surf.tracker.userauth.infrastructure.security.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitConfig(RoleControllerIntegrationTest.Config.class)
@WebAppConfiguration
class RoleControllerIntegrationTest {
    @Configuration
    @EnableWebMvc
    @Import({SecurityConfig.class, JwtAuthFilter.class, AuthIdentityService.class,
        AuthRateLimitFilter.class, RoleController.class,
        GlobalExceptionHandler.class})
    static class Config {
        @Bean java.time.Clock clock() { return java.time.Clock.systemUTC(); }
        @Bean JwtService jwtService() {
            var config = new JwtConfig();
            config.setSecret("role-catalog-test-only-secret-at-least-thirty-two-bytes");
            return new JwtService(config);
        }
        @Bean UserRepository users() { return mock(UserRepository.class); }
        @Bean SessionRepository sessions() { return mock(SessionRepository.class); }
        @Bean RoleRepository roles() { return mock(RoleRepository.class); }
        @Bean AuthService authService(UserRepository users, SessionRepository sessions, RoleRepository roles) {
            return new AuthService(users, mock(OrganizationRepository.class), roles,
                mock(PermissionRepository.class), sessions, mock(RefreshTokenRepository.class),
                mock(LoginAttemptRepository.class), mock(PasswordService.class), mock(TokenHashService.class),
                jwtService(), new JwtConfig(), new LoginPolicyConfig(), clock());
        }
    }

    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired SessionRepository sessions;
    @Autowired RoleRepository roles;
    @Autowired AuthService auth;
    @Autowired JwtService jwt;
    MockMvc mvc;
    User user;
    Session session;
    Role admin;

    @BeforeEach
    void setUp() {
        reset(users, sessions, roles);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        var org = Organization.create("Catalog", "catalog");
        org.setId(10L);
        admin = Role.orgRole(org, "TENANT_ADMIN", "Tenant administrator");
        admin.setId(7L);
        admin.addPermission(Permission.of("roles.read", "Read roles"));
        admin.addPermission(Permission.of("alerts.read", "Read alerts"));
        user = new User();
        user.setId(1L);
        user.setEmail("catalog@example.test");
        user.assignRole(admin);
        session = Session.create(user, "hash", Instant.parse("2099-01-01T00:00:00Z"));
        session.setOrgId(10L);
        when(users.findWithAuthoritiesById(1L)).thenReturn(Optional.of(user));
        when(sessions.findBySessionUuidAndUserId(session.getSessionUuid(), 1L)).thenReturn(Optional.of(session));
    }

    @AfterEach
    void clearContext() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    String token(Long orgId) {
        return jwt.generateAccessToken(1L, orgId, user.getEmail(), List.of("SUPER_ADMIN"), session.getSessionUuid());
    }

    @Test
    void list_tenantAdmin_returnsBoundedSortedPrivateCatalogIgnoringOrgOverride() throws Exception {
        // given
        var pageable = PageRequest.of(0, 1, Sort.by("name", "id"));
        when(roles.findByOrgId(10L, pageable)).thenReturn(new PageImpl<>(List.of(admin), pageable, 2));
        // when / then
        mvc.perform(get("/api/v1/roles?size=1&orgId=20&organization_id=20")
                .header("Authorization", "Bearer " + token(10L)))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "private, no-store"))
            .andExpect(jsonPath("$.content[0].id").value(7))
            .andExpect(jsonPath("$.content[0].name").value("TENANT_ADMIN"))
            .andExpect(jsonPath("$.content[0].description").value("Tenant administrator"))
            .andExpect(jsonPath("$.content[0].permissions[0]").value("alerts.read"))
            .andExpect(jsonPath("$.content[0].permissions[1]").value("roles.read"))
            .andExpect(jsonPath("$.totalElements").value(2));
        verify(roles).findByOrgId(10L, pageable);
        verifyNoMoreInteractions(roles);
    }

    @Test
    void list_viewerWithForgedAdminClaim_returns403() throws Exception {
        // given
        user.clearRoles();
        user.assignRole(Role.orgRole(admin.getOrg(), "VIEWER", "Viewer"));
        // when / then
        mvc.perform(get("/api/v1/roles").header("Authorization", "Bearer " + token(10L)))
            .andExpect(status().isForbidden());
        verifyNoInteractions(roles);
    }

    @Test
    void list_anonymous_returns401() throws Exception {
        // when / then
        mvc.perform(get("/api/v1/roles")).andExpect(status().isUnauthorized());
        verifyNoInteractions(roles);
    }

    @Test
    void list_globalAdminWithoutTenant_returns403() throws Exception {
        // given
        user.clearRoles();
        var global = Role.systemRole("SUPER_ADMIN", "Global");
        global.addPermission(Permission.of("roles.read", "Read roles"));
        user.assignRole(global);
        session.setOrgId(null);
        // when / then
        mvc.perform(get("/api/v1/roles").header("Authorization", "Bearer " + token(null)))
            .andExpect(status().isForbidden());
        verifyNoInteractions(roles);
    }

    @ParameterizedTest
    @CsvSource({"-1,50", "0,0", "0,101", "0,-1"})
    void list_invalidPagination_returns400(int page, int size) throws Exception {
        // when / then
        mvc.perform(get("/api/v1/roles?page=" + page + "&size=" + size)
                .header("Authorization", "Bearer " + token(10L)))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(roles);
    }

    @Test
    void listRoles_directUseCaseWithoutPermission_deniesBeforeRepository() {
        // given
        TenantContext.setCurrentOrgId(10L);
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("viewer", "unused", List.of()));
        // when / then
        assertThatThrownBy(() -> auth.listRoles(0, 50)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(roles);
    }

    @Test
    void listRoles_directUseCaseWithoutTenant_deniesBeforeRepository() {
        // given
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            "reader", "unused", List.of(new SimpleGrantedAuthority("roles.read"))));
        // when / then
        assertThatThrownBy(() -> auth.listRoles(0, 50)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(roles);
    }
}
