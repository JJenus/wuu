package com.surf.tracker.userauth.application.service;

import com.surf.tracker.shared.exception.BusinessRuleException;
import com.surf.tracker.shared.exception.DomainException;
import com.surf.tracker.shared.exception.ValidationException;
import com.surf.tracker.userauth.application.dto.LoginResponse;
import com.surf.tracker.userauth.application.dto.RegisterRequest;
import com.surf.tracker.userauth.application.dto.RegisterResponse;
import com.surf.tracker.userauth.application.dto.UserResponse;
import com.surf.tracker.userauth.domain.entity.*;
import com.surf.tracker.userauth.infrastructure.repository.*;
import com.surf.tracker.userauth.infrastructure.security.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final Instant FIXED = Instant.parse("2025-01-01T12:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED, ZoneOffset.UTC);

    @Mock private UserRepository userRepository;
    @Mock private OrganizationRepository organizationRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private PermissionRepository permissionRepository;
    @Mock private SessionRepository sessionRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private LoginAttemptRepository loginAttemptRepository;

    private PasswordService passwordService;
    private TokenHashService tokenHashService;
    private JwtService jwtService;
    private JwtConfig jwtConfig;
    private LoginPolicyConfig policy;
    private AuthService service;

    @BeforeEach
    void setUp() {
        passwordService = new PasswordService(10);
        tokenHashService = new TokenHashService();
        jwtConfig = new JwtConfig();
        jwtConfig.setSecret("test-secret-that-is-at-least-32-bytes-long-for-hs256-signing");
        jwtConfig.setAccessTokenExpiry(Duration.ofMinutes(15));
        jwtConfig.setRefreshTokenExpiry(Duration.ofDays(7));
        jwtService = new JwtService(jwtConfig, FIXED_CLOCK);
        policy = new LoginPolicyConfig();
        policy.setMaxFailedAttempts(5);
        policy.setFailedAttemptsWindow(Duration.ofMinutes(30));

        service = new AuthService(
            userRepository, organizationRepository, roleRepository, permissionRepository,
            sessionRepository, refreshTokenRepository, loginAttemptRepository,
            passwordService, tokenHashService, jwtService, jwtConfig, policy, FIXED_CLOCK
        );
        org.springframework.test.util.ReflectionTestUtils.setField(service, "registrationEnabled", true);
    }

    @Test
    void register_validRequest_createsOrgAndUser() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("alice@example.com");
        request.setPassword("test-password-123");
        request.setFirstName("Alice");
        request.setLastName("Wonder");
        request.setOrganizationName("Wonder Inc");

        when(organizationRepository.existsBySlug(anyString())).thenReturn(false);
        when(organizationRepository.save(any(Organization.class))).thenAnswer(inv -> {
            Organization o = inv.getArgument(0);
            o.setId(1L);
            return o;
        });
        when(roleRepository.save(any(Role.class))).thenAnswer(inv -> {
            Role r = inv.getArgument(0);
            r.setId(1L);
            return r;
        });
        when(roleRepository.findByNameAndOrgId(eq("TENANT_ADMIN"), anyLong()))
            .thenReturn(Optional.of(Role.orgRole(Organization.create("Wonder Inc", "wonder-inc"), "TENANT_ADMIN", "x")));
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(1L);
            return u;
        });
        when(sessionRepository.save(any(Session.class))).thenAnswer(inv -> {
            Session s = inv.getArgument(0);
            s.setId(1L);
            return s;
        });
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> {
            RefreshToken rt = inv.getArgument(0);
            rt.setId(1L);
            return rt;
        });

        RegisterResponse response = service.register(request, "127.0.0.1", "test-agent");

        assertThat(response).isNotNull();
        assertThat(response.getUserId()).isEqualTo(1L);
        assertThat(response.getOrganizationId()).isEqualTo(1L);
        assertThat(response.getAccessToken()).isNotBlank();
        assertThat(response.getRefreshToken()).isNotBlank();
        assertThat(response.getExpiresIn()).isEqualTo(15 * 60);
        assertThat(response.getRefreshExpiresIn()).isEqualTo(7 * 24 * 60 * 60);

        ArgumentCaptor<LoginAttempt> attemptCaptor = ArgumentCaptor.forClass(LoginAttempt.class);
        verify(loginAttemptRepository).save(attemptCaptor.capture());
        assertThat(attemptCaptor.getValue().isSuccess()).isTrue();
    }

    @Test
    void register_duplicateEmail_throws() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("dup@example.com");
        request.setPassword("test-password-123");
        request.setFirstName("Dup");
        request.setLastName("User");
        request.setOrganizationName("Dup Inc");

        when(userRepository.existsByEmail("dup@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.register(request, "127.0.0.1", null))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("already");
    }

    @Test
    void login_validCredentials_returnsTokens() {
        User user = makeUserWithHash("alice@example.com", passwordService.hash("test-password-123"));
        user.setId(1L);
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(loginAttemptRepository.countByUserIdAndSuccessFalseAndAttemptedAtAfter(anyLong(), any())).thenReturn(0L);
        when(sessionRepository.save(any(Session.class))).thenAnswer(inv -> {
            Session s = inv.getArgument(0);
            s.setId(1L);
            return s;
        });
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> {
            RefreshToken rt = inv.getArgument(0);
            rt.setId(1L);
            return rt;
        });

        LoginResponse response = service.login("alice@example.com", "test-password-123", "127.0.0.1", "agent");

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isNotBlank();
        assertThat(response.getRefreshToken()).isNotBlank();
        assertThat(response.getRefreshExpiresIn()).isEqualTo(7 * 24 * 60 * 60);
    }

    @Test
    void login_unknownEmail_throws() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login("nobody@example.com", "any", "127.0.0.1", null))
            .isInstanceOf(AuthException.class);
    }

    @Test
    void login_wrongPassword_throws() {
        User user = makeUserWithHash("alice@example.com", passwordService.hash("rightPassword"));
        user.setId(1L);
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(loginAttemptRepository.countByUserIdAndSuccessFalseAndAttemptedAtAfter(anyLong(), any())).thenReturn(0L);

        assertThatThrownBy(() -> service.login("alice@example.com", "wrong", "127.0.0.1", null))
            .isInstanceOf(AuthException.class);

        ArgumentCaptor<LoginAttempt> captor = ArgumentCaptor.forClass(LoginAttempt.class);
        verify(loginAttemptRepository).save(captor.capture());
        assertThat(captor.getValue().isFailure()).isTrue();
    }

    @Test
    void login_lockedAccount_throws() {
        User user = makeUserWithHash("alice@example.com", passwordService.hash("test-password-123"));
        user.setId(1L);
        user.lock();
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(loginAttemptRepository.countByUserIdAndSuccessFalseAndAttemptedAtAfter(anyLong(), any())).thenReturn(0L);

        assertThatThrownBy(() -> service.login("alice@example.com", "test-password-123", "127.0.0.1", null))
            .isInstanceOf(AuthException.class)
            .hasMessageContaining("invalid email or password");
    }

    @Test
    void login_disabledUser_throws() {
        User user = makeUserWithHash("alice@example.com", passwordService.hash("test-password-123"));
        user.setId(1L);
        user.setEnabled(false);
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(loginAttemptRepository.countByUserIdAndSuccessFalseAndAttemptedAtAfter(anyLong(), any())).thenReturn(0L);

        assertThatThrownBy(() -> service.login("alice@example.com", "test-password-123", "127.0.0.1", null))
            .isInstanceOf(AuthException.class)
            .hasMessageContaining("invalid email or password");
    }

    @Test
    void login_exceedsMaxFailedAttempts_rejectsWithoutPermanentLock() {
        User user = makeUserWithHash("alice@example.com", passwordService.hash("test-password-123"));
        user.setId(1L);
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(loginAttemptRepository.countByUserIdAndSuccessFalseAndAttemptedAtAfter(anyLong(), any()))
            .thenReturn(5L);

        assertThatThrownBy(() -> service.login("alice@example.com", "test-password-123", "127.0.0.1", null))
            .isInstanceOf(AuthException.class);

        assertThat(user.isLocked()).isFalse();
    }

    @Test
    void refresh_validToken_rotatesAndIssuesNew() {
        User user = makeUserWithHash("alice@example.com", passwordService.hash("test-password-123"));
        user.setId(1L);
        Session session = Session.create(user, "sessionHash", FIXED.plusSeconds(3600));
        session.setId(10L);
        RefreshToken existing = RefreshToken.issue(session, tokenHashService.hash("refresh-raw"), FIXED.plusSeconds(604800));
        existing.setId(20L);
        when(refreshTokenRepository.findForRotation(anyString())).thenReturn(Optional.of(existing));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> {
            RefreshToken rt = inv.getArgument(0);
            if (rt.getId() == null) rt.setId(21L);
            return rt;
        });

        LoginResponse response = service.refresh("refresh-raw", "127.0.0.1", "agent");

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isNotBlank();
        assertThat(response.getRefreshToken()).isNotBlank();
        assertThat(response.getRefreshExpiresIn()).isEqualTo(3600);
        assertThat(existing.getRevokedAt()).isNotNull();
    }

    @Test
    void refresh_rotatedTokenRevoked_throwsAndRevokesChain() {
        User user = makeUserWithHash("alice@example.com", passwordService.hash("test-password-123"));
        user.setId(1L);
        Session session = Session.create(user, "sessionHash", FIXED.plusSeconds(3600));
        session.setId(10L);
        RefreshToken rotatedToken = RefreshToken.issue(session, "tokHash", FIXED.plusSeconds(604800));
        rotatedToken.setId(20L);
        RefreshToken previousToken = RefreshToken.issue(session, "prevHash", FIXED.plusSeconds(604800));
        previousToken.setId(19L);
        rotatedToken.setRotatedFrom(previousToken);
        rotatedToken.revokeAt(FIXED.minusSeconds(1));
        when(refreshTokenRepository.findForRotation(anyString())).thenReturn(Optional.of(rotatedToken));
        when(refreshTokenRepository.findBySessionId(10L)).thenReturn(java.util.List.of(rotatedToken, previousToken));

        assertThatThrownBy(() -> service.refresh("any-raw", "127.0.0.1", "agent"))
            .isInstanceOf(com.surf.tracker.userauth.infrastructure.security.SecurityException.class)
            .hasMessageContaining("reuse");

        assertThat(rotatedToken.getRevokedAt()).isNotNull();
        assertThat(session.getRevokedAt()).isNotNull();
    }

    @Test
    void refresh_invalidToken_throws() {
        when(refreshTokenRepository.findForRotation(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.refresh("any-raw", "127.0.0.1", "agent"))
            .isInstanceOf(AuthException.class);
    }

    @Test
    void refresh_blankToken_throws() {
        assertThatThrownBy(() -> service.refresh("", "127.0.0.1", "agent"))
            .isInstanceOf(AuthException.class);
    }

    @Test
    void changePassword_correctOldPassword_updatesHash() {
        User user = makeUserWithHash("alice@example.com", passwordService.hash("oldPassword!"));
        user.setId(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        service.changePassword(1L, "oldPassword!", "newPassword123", "127.0.0.1", "agent");

        assertThat(user.getPasswordHash()).isNotEqualTo(passwordService.hash("oldPassword!"));
        assertThat(passwordService.matches("newPassword123", user.getPasswordHash())).isTrue();
    }

    @Test
    void changePassword_wrongOldPassword_throws() {
        User user = makeUserWithHash("alice@example.com", passwordService.hash("oldPassword!"));
        user.setId(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.changePassword(1L, "wrong", "newPassword123", "127.0.0.1", "agent"))
            .isInstanceOf(AuthException.class);
    }

    @Test
    void getCurrentUser_userNotFound_throws() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCurrentUser(99L))
            .isInstanceOf(DomainException.class);
    }

    @Test
    void getUserById_userInOrg_returnsUserResponse() {
        // given
        User user = userInOrg(1L);
        when(userRepository.findByIdAndOrgId(1L, 10L)).thenReturn(Optional.of(user));

        // when
        UserResponse response = service.getUserById(1L, 10L);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getEmail()).isEqualTo("alice@example.com");
        assertThat(response.getOrganizationId()).isEqualTo(10L);
    }

    @Test
    void getUserById_userNotInOrg_throws() {
        // given
        when(userRepository.findByIdAndOrgId(1L, 10L)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> service.getUserById(1L, 10L))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("not found");
    }

    @Test
    void updateUserProfile_validRequest_updatesFields() {
        // given
        User user = userInOrg(1L);
        when(userRepository.findByIdAndOrgId(1L, 10L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);

        // when
        UserResponse response = service.updateUserProfile(1L, 10L, "Alice", "Smith", "alice@new.com");

        // then
        assertThat(response).isNotNull();
        assertThat(user.getFirstName()).isEqualTo("Alice");
        assertThat(user.getLastName()).isEqualTo("Smith");
        assertThat(user.getEmail()).isEqualTo("alice@new.com");
        verify(userRepository).save(user);
    }

    @Test
    void updateUserProfile_emailTaken_throws() {
        // given
        User user = userInOrg(1L);
        user.setEmail("alice@example.com");
        when(userRepository.findByIdAndOrgId(1L, 10L)).thenReturn(Optional.of(user));
        when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);

        // when & then
        assertThatThrownBy(() -> service.updateUserProfile(1L, 10L, null, null, "taken@example.com"))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("already");
    }

    @Test
    void deleteUser_validRequest_deletes() {
        // given
        User user = userInOrg(1L);
        when(userRepository.findByIdAndOrgId(1L, 10L)).thenReturn(Optional.of(user));

        // when
        service.deleteUser(1L, 10L);

        // then
        verify(userRepository).save(user);
        assertThat(user.getRoles()).isEmpty();
    }

    @Test
    void deleteUser_userNotInOrg_throws() {
        // given
        when(userRepository.findByIdAndOrgId(1L, 10L)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> service.deleteUser(1L, 10L))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("not found");
    }

    private User userInOrg(Long userId) {
        User u = makeUserWithHash("alice@example.com", "hash");
        u.setId(userId);
        Organization org = Organization.create("Acme", "acme");
        org.setId(10L);
        u.assignRole(Role.orgRole(org, "OPERATOR", "operator for acme"));
        return u;
    }

    @Test
    void assignRoles_systemRole_rejectsWithoutChangingMembership() {
        // given
        User user = userInOrg(1L);
        when(userRepository.findByIdAndOrgId(1L, 10L)).thenReturn(Optional.of(user));
        when(roleRepository.findById(99L)).thenReturn(Optional.of(Role.systemRole("SUPER_ADMIN", "system")));
        // when / then
        assertThatThrownBy(() -> service.assignRoles(1L, java.util.List.of(99L), 10L))
            .isInstanceOf(ValidationException.class);
        assertThat(user.getRoles()).extracting(Role::getName).containsExactly("OPERATOR");
        verify(userRepository, never()).save(any());
    }

    @Test
    void assignRoles_foreignUser_rejects() {
        // given
        when(userRepository.findByIdAndOrgId(2L, 10L)).thenReturn(Optional.empty());
        // when / then
        assertThatThrownBy(() -> service.assignRoles(2L, java.util.List.of(99L), 10L))
            .isInstanceOf(BusinessRuleException.class);
        verifyNoInteractions(roleRepository);
    }

    @Test
    void refresh_successorToken_canRotateAgain() {
        // given
        User user = userInOrg(1L);
        Session session = Session.create(user, "session", FIXED.plusSeconds(600));
        session.setOrgId(10L);
        RefreshToken first = RefreshToken.issue(session, "first", session.getExpiresAt());
        RefreshToken second = RefreshToken.rotateFrom(session, first, "second", session.getExpiresAt());
        when(refreshTokenRepository.findForRotation(anyString())).thenReturn(Optional.of(second));
        // when
        LoginResponse response = service.refresh("raw", "127.0.0.1", null);
        // then
        assertThat(response.getRefreshToken()).isNotBlank();
        assertThat(second.getRevokedAt()).isEqualTo(FIXED);
    }

    @Test
    void refresh_revokedSession_rejects() {
        // given
        Session session = Session.create(userInOrg(1L), "session", FIXED.plusSeconds(600));
        session.revokeAt(FIXED);
        RefreshToken token = RefreshToken.issue(session, "hash", session.getExpiresAt());
        when(refreshTokenRepository.findForRotation(anyString())).thenReturn(Optional.of(token));
        // when / then
        assertThatThrownBy(() -> service.refresh("raw", "127.0.0.1", null)).isInstanceOf(AuthException.class);
    }

    @Test
    void logout_userSessions_revokesAccessAndRefreshSession() {
        // given
        Session session = Session.create(userInOrg(1L), "session", FIXED.plusSeconds(600));
        session.setId(10L);
        RefreshToken token = RefreshToken.issue(session, "hash", session.getExpiresAt());
        when(sessionRepository.findByUserId(1L)).thenReturn(java.util.List.of(session));
        when(refreshTokenRepository.findBySessionId(10L)).thenReturn(java.util.List.of(token));
        // when
        service.logout(1L, null, null);
        // then
        assertThat(session.getRevokedAt()).isEqualTo(FIXED);
        assertThat(token.getRevokedAt()).isEqualTo(FIXED);
    }

    private User makeUserWithHash(String email, String hash) {
        User u = new User();
        u.setEmail(email);
        u.setPasswordHash(hash);
        return u;
    }
}
