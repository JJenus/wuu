package com.surf.tracker.userauth.application.service;

import com.surf.tracker.shared.exception.BusinessRuleException;
import com.surf.tracker.shared.exception.ValidationException;
import com.surf.tracker.shared.util.TimeProvider;
import com.surf.tracker.userauth.application.dto.LoginResponse;
import com.surf.tracker.userauth.application.dto.RegisterRequest;
import com.surf.tracker.userauth.application.dto.RegisterResponse;
import com.surf.tracker.userauth.application.dto.UserResponse;
import com.surf.tracker.userauth.domain.entity.*;
import com.surf.tracker.userauth.domain.enums.FailureReason;
import com.surf.tracker.userauth.infrastructure.repository.*;
import com.surf.tracker.userauth.infrastructure.security.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AuthService {

    @org.springframework.beans.factory.annotation.Value("${userauth.registration-enabled:false}")
    private boolean registrationEnabled;

    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final SessionRepository sessionRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final LoginAttemptRepository loginAttemptRepository;
    private final PasswordService passwordService;
    private final TokenHashService tokenHashService;
    private final JwtService jwtService;
    private final JwtConfig jwtConfig;
    private final LoginPolicyConfig policy;
    private final Clock clock;

    public AuthService(UserRepository userRepository,
                       OrganizationRepository organizationRepository,
                       RoleRepository roleRepository,
                       PermissionRepository permissionRepository,
                       SessionRepository sessionRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       LoginAttemptRepository loginAttemptRepository,
                       PasswordService passwordService,
                       TokenHashService tokenHashService,
                       JwtService jwtService,
                       JwtConfig jwtConfig,
                       LoginPolicyConfig policy,
                       Clock clock) {
        this.userRepository = userRepository;
        this.organizationRepository = organizationRepository;
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.sessionRepository = sessionRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.loginAttemptRepository = loginAttemptRepository;
        this.passwordService = passwordService;
        this.tokenHashService = tokenHashService;
        this.jwtService = jwtService;
        this.jwtConfig = jwtConfig;
        this.policy = policy;
        this.clock = clock;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request, String ip, String userAgent) {
        if (!registrationEnabled) throw new org.springframework.security.access.AccessDeniedException("Registration is disabled");
        if (userRepository.existsByEmail(request.getEmail().trim().toLowerCase(Locale.ROOT))) {
            throw new ValidationException("EMAIL_TAKEN", "email already registered");
        }

        Organization org = Organization.create(request.getOrganizationName(), generateSlug(request.getOrganizationName()));
        organizationRepository.save(org);
        seedOrgRoles(org);

        User user = new User();
        user.setEmail(request.getEmail().trim().toLowerCase(Locale.ROOT));
        user.setPasswordHash(passwordService.hash(request.getPassword()));
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        userRepository.save(user);

        Role tenantAdmin = roleRepository
            .findByNameAndOrgId("TENANT_ADMIN", org.getId())
            .orElseThrow(() -> new BusinessRuleException("ROLE_MISSING", "TENANT_ADMIN role missing"));
        user.assignRole(tenantAdmin);
        userRepository.save(user);

        IssuedTokens issued = issueTokens(user, org.getId());
        loginAttemptRepository.save(LoginAttempt.success(user, ip, userAgent));

        return new RegisterResponse(
            user.getId(), org.getId(), issued.accessToken(),
            issued.refreshToken(), jwtService.getAccessTokenExpirySeconds()
        );
    }

    @Transactional(noRollbackFor = AuthException.class)
    public LoginResponse login(String email, String password, String ip, String userAgent) {
        Optional<User> userOpt = userRepository.findByEmail(email.trim().toLowerCase(Locale.ROOT));
        User user = userOpt.orElse(null);

        if (user == null) {
            passwordService.checkUnknownAccount(password);
            loginAttemptRepository.save(LoginAttempt.failure(null, ip, userAgent, FailureReason.USER_NOT_FOUND));
            throw new AuthException("INVALID_CREDENTIALS", "invalid email or password");
        }

        Instant now = Instant.now(clock);
        long recentFailures = loginAttemptRepository.countByUserIdAndSuccessFalseAndAttemptedAtAfter(
            user.getId(), now.minus(policy.getFailedAttemptsWindow()));
        if (recentFailures >= policy.getMaxFailedAttempts()) {
            loginAttemptRepository.save(LoginAttempt.failure(user, ip, userAgent, FailureReason.ACCOUNT_LOCKED));
            throw new AuthException("INVALID_CREDENTIALS", "invalid email or password");
        }

        if (user.isLocked()) {
            loginAttemptRepository.save(LoginAttempt.failure(user, ip, userAgent, FailureReason.USER_LOCKED));
            passwordService.checkUnknownAccount(password);
            throw new AuthException("INVALID_CREDENTIALS", "invalid email or password");
        }
        if (!user.isEnabled()) {
            loginAttemptRepository.save(LoginAttempt.failure(user, ip, userAgent, FailureReason.USER_DISABLED));
            passwordService.checkUnknownAccount(password);
            throw new AuthException("INVALID_CREDENTIALS", "invalid email or password");
        }
        if (!passwordService.matches(password, user.getPasswordHash())) {
            loginAttemptRepository.save(LoginAttempt.failure(user, ip, userAgent, FailureReason.INVALID_CREDENTIALS));
            throw new AuthException("INVALID_CREDENTIALS", "invalid email or password");
        }

        IssuedTokens issued = issueTokens(user, primaryOrgId(user));
        loginAttemptRepository.save(LoginAttempt.success(user, ip, userAgent));
        return new LoginResponse(issued.accessToken(), issued.refreshToken(),
            jwtService.getAccessTokenExpirySeconds());
    }

    @Transactional(noRollbackFor = {AuthException.class, com.surf.tracker.userauth.infrastructure.security.SecurityException.class})
    public LoginResponse refresh(String rawRefreshToken, String ip, String userAgent) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new AuthException("INVALID_TOKEN", "refresh token required");
        }
        String hash = tokenHashService.hash(rawRefreshToken);
        Instant now = Instant.now(clock);
        RefreshToken existing = refreshTokenRepository.findForRotation(hash)
            .orElseThrow(() -> {
                loginAttemptRepository.save(LoginAttempt.failure(null, ip, userAgent, FailureReason.INVALID_TOKEN));
                return new AuthException("INVALID_TOKEN", "refresh token invalid or expired");
            });

        if (existing.hasBeenRotated()) {
            revokeChain(existing);
            loginAttemptRepository.save(LoginAttempt.failure(existing.getSession().getUser(), ip, userAgent, FailureReason.REUSED_TOKEN));
            throw new com.surf.tracker.userauth.infrastructure.security.SecurityException("REUSED_TOKEN", "refresh token reuse detected; session revoked");
        }

        Session session = existing.getSession();
        User user = session.getUser();
        if (!existing.isActive(now) || !session.isActive(now) || !user.canAuthenticate()) {
            throw new AuthException("INVALID_TOKEN", "refresh token invalid or expired");
        }
        Long orgId = session.getOrgId();
        if (orgId != null && user.getRoles().stream().noneMatch(role -> role.isOrgScoped()
                && Objects.equals(role.getOrg().getId(), orgId))) {
            throw new AuthException("INVALID_TOKEN", "refresh token invalid or expired");
        }

        existing.revokeAt(now);
        refreshTokenRepository.save(existing);

        IssuedTokens issued = rotateTokens(user, orgId, session, existing);
        return new LoginResponse(issued.accessToken(), issued.refreshToken(),
            jwtService.getAccessTokenExpirySeconds());
    }

    @Transactional
    public void logout(Long userId, String ip, String userAgent) {
        if (userId == null) return;
        Instant now = Instant.now(clock);
            for (Session s : sessionRepository.findByUserId(userId)) {
                if (s.getRevokedAt() == null) {
                    s.revokeAt(now);
                    sessionRepository.save(s);
                    for (RefreshToken rt : refreshTokenRepository.findBySessionId(s.getId())) {
                        if (rt.getRevokedAt() == null) {
                            rt.revokeAt(now);
                            refreshTokenRepository.save(rt);
                        }
                    }
                }
            }
    }

    @Transactional
    public void changePassword(Long userId, String oldPassword, String newPassword, String ip, String userAgent) {
        if (userId == null) {
            throw new AuthException("UNAUTHENTICATED", "authentication required");
        }
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessRuleException("USER_NOT_FOUND", "user not found"));
        if (!passwordService.matches(oldPassword, user.getPasswordHash())) {
            loginAttemptRepository.save(LoginAttempt.failure(user, ip, userAgent, FailureReason.INVALID_CREDENTIALS));
            throw new AuthException("INVALID_CREDENTIALS", "current password incorrect");
        }
        passwordService.validateStrength(newPassword);
        user.setPasswordHash(passwordService.hash(newPassword));
        userRepository.save(user);
        logout(userId, ip, userAgent);
    }

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessRuleException("USER_NOT_FOUND", "user not found"));
        return toResponse(user);
    }

    @Transactional
    public UserResponse createUser(String email, String password, String firstName, String lastName, Long orgId) {
        if (orgId == null) throw new org.springframework.security.access.AccessDeniedException("Tenant required");
        if (userRepository.existsByEmail(email.toLowerCase(Locale.ROOT))) {
            throw new ValidationException("EMAIL_TAKEN", "email already registered");
        }
        User u = new User();
        u.setEmail(email.trim().toLowerCase(Locale.ROOT));
        u.setPasswordHash(passwordService.hash(password));
        u.setFirstName(firstName);
        u.setLastName(lastName);
        userRepository.save(u);
        if (orgId != null) {
            Role role = roleRepository.findByNameAndOrgId("OPERATOR", orgId).orElse(null);
            if (role != null) {
                u.assignRole(role);
                userRepository.save(u);
            }
        }
        return toResponse(u);
    }

    @Transactional
    public UserResponse assignRoles(Long userId, List<Long> roleIds, Long orgId) {
        if (orgId == null) throw new org.springframework.security.access.AccessDeniedException("Tenant required");
        organizationRepository.lockById(orgId);
        User user = userRepository.findByIdAndOrgId(userId, orgId)
            .orElseThrow(() -> new BusinessRuleException("USER_NOT_FOUND", "user not found"));
        Set<Role> assigned = new HashSet<>();
        for (Long roleId : roleIds) {
            Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new BusinessRuleException("ROLE_NOT_FOUND", "role " + roleId + " not found"));
            if (!role.isOrgScoped() || !Objects.equals(role.getOrg().getId(), orgId)) {
                throw new ValidationException("ROLE_ORG_MISMATCH",
                    "role " + role.getName() + " is not scoped to org " + orgId);
            }
            assigned.add(role);
        }
        protectLastAdmin(user, orgId, assigned.stream().anyMatch(role -> "TENANT_ADMIN".equals(role.getName())));
        // Preserve memberships in other tenants; a tenant admin cannot edit them.
        user.getRoles().removeIf(role -> role.isOrgScoped() && Objects.equals(role.getOrg().getId(), orgId));
        assigned.forEach(user::assignRole);
        userRepository.save(user);
        return toResponse(user);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> listUsersInOrg(Long orgId) {
        return userRepository.findByOrgId(orgId,
                org.springframework.data.domain.PageRequest.of(0, 1000))
            .map(this::toResponse)
            .getContent();
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> listUsersInOrgPaged(Long orgId, Pageable pageable) {
        return userRepository.findByOrgId(orgId, pageable)
            .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public UserResponse getUserById(Long userId, Long orgId) {
        User user = userRepository.findByIdAndOrgId(userId, orgId)
            .orElseThrow(() -> new BusinessRuleException("USER_NOT_FOUND", "user not found"));
        return toResponse(user);
    }

    @Transactional
    public UserResponse updateUserProfile(Long userId, Long orgId, String firstName, String lastName, String email) {
        User user = userRepository.findByIdAndOrgId(userId, orgId)
            .orElseThrow(() -> new BusinessRuleException("USER_NOT_FOUND", "user not found"));
        if (user.getRoles().stream().anyMatch(role -> role.isSystemRole()
                || !Objects.equals(role.getOrg().getId(), orgId))) {
            throw new org.springframework.security.access.AccessDeniedException("Cross-tenant identities require out-of-band administration");
        }
        if (firstName != null) {
            user.setFirstName(firstName);
        }
        if (lastName != null) {
            user.setLastName(lastName);
        }
        if (email != null && !email.isBlank()) {
            String normalized = email.trim().toLowerCase(Locale.ROOT);
            if (!normalized.equals(user.getEmail()) && userRepository.existsByEmail(normalized)) {
                throw new ValidationException("EMAIL_TAKEN", "email already registered");
            }
            user.setEmail(normalized);
        }
        return toResponse(userRepository.save(user));
    }

    @Transactional
    public void deleteUser(Long userId, Long orgId) {
        organizationRepository.lockById(orgId);
        User user = userRepository.findByIdAndOrgId(userId, orgId)
            .orElseThrow(() -> new BusinessRuleException("USER_NOT_FOUND", "user not found"));
        protectLastAdmin(user, orgId, false);
        // Tenant administrators remove membership, never delete a cross-tenant identity.
        user.getRoles().removeIf(role -> role.isOrgScoped() && Objects.equals(role.getOrg().getId(), orgId));
        userRepository.save(user);
        logout(userId, null, null);
    }

    private void protectLastAdmin(User user, Long orgId, boolean remainsAdmin) {
        boolean admin = user.getRoles().stream().anyMatch(role -> role.isOrgScoped()
            && Objects.equals(role.getOrg().getId(), orgId) && "TENANT_ADMIN".equals(role.getName()));
        if (admin && !remainsAdmin && userRepository.countAdminsInOrg(orgId) <= 1) {
            throw new ValidationException("LAST_ADMIN", "cannot remove the last tenant administrator");
        }
    }

    private void seedOrgRoles(Organization org) {
        Map<String, List<String>> rolePerms = Map.of(
            "TENANT_ADMIN", List.of("users.read", "users.write", "users.delete", "users.assign_roles",
                "organizations.read", "roles.read", "roles.write",
                "vehicles.read", "vehicles.write", "vehicles.assign_device",
                "alerts.read", "alerts.write", "devices.read", "devices.command", "trips.read", "trips.export",
                "notifications.read", "notifications.write", "organizations.write"),
            "OPERATOR", List.of("users.read", "vehicles.read", "alerts.read", "alerts.write",
                "trips.read", "notifications.read", "notifications.write"),
            "VIEWER", List.of("vehicles.read", "alerts.read", "trips.read")
        );

        for (Map.Entry<String, List<String>> e : rolePerms.entrySet()) {
            Role role = Role.orgRole(org, e.getKey(), e.getKey() + " for org " + org.getSlug());
            for (String key : e.getValue()) {
                Permission p = permissionRepository.findByKey(key).orElseGet(() -> {
                    Permission created = Permission.of(key, key);
                    permissionRepository.save(created);
                    return created;
                });
                role.addPermission(p);
            }
            roleRepository.save(role);
        }
    }

    private IssuedTokens issueTokens(User user, Long orgId) {
        String sessionRaw = tokenHashService.generateOpaqueToken();
        String sessionHash = tokenHashService.hash(sessionRaw);
        Instant sessionExpires = Instant.now(clock).plus(jwtConfig.getRefreshTokenExpiry());
        Session session = Session.create(user, sessionHash, sessionExpires);
        session.setOrgId(orgId);
        sessionRepository.save(session);

        String refreshRaw = tokenHashService.generateOpaqueToken();
        String refreshHash = tokenHashService.hash(refreshRaw);
        Instant refreshExpires = Instant.now(clock).plus(jwtConfig.getRefreshTokenExpiry());
        RefreshToken refresh = RefreshToken.issue(session, refreshHash, refreshExpires);
        refreshTokenRepository.save(refresh);

        String access = generateAccessToken(user, orgId, session);
        return new IssuedTokens(access, refreshRaw);
    }

    private IssuedTokens rotateTokens(User user, Long orgId, Session session, RefreshToken oldToken) {
        String refreshRaw = tokenHashService.generateOpaqueToken();
        String refreshHash = tokenHashService.hash(refreshRaw);
        Instant refreshExpires = session.getExpiresAt();
        RefreshToken next = RefreshToken.rotateFrom(session, oldToken, refreshHash, refreshExpires);
        refreshTokenRepository.save(next);
        String access = generateAccessToken(user, orgId, session);
        return new IssuedTokens(access, refreshRaw);
    }

    private String generateAccessToken(User user, Long orgId, Session session) {
        List<String> roleNames = user.getRoles().stream().map(Role::getName).distinct().collect(Collectors.toList());
        return jwtService.generateAccessToken(user.getId(), orgId, user.getEmail(), roleNames, session.getSessionUuid());
    }

    private UserResponse toResponse(User u) {
        UserResponse r = new UserResponse();
        r.setId(u.getId());
        r.setEmail(u.getEmail());
        r.setFirstName(u.getFirstName());
        r.setLastName(u.getLastName());
        r.setEnabled(u.isEnabled());
        r.setLocked(u.isLocked());
        r.setCreatedAt(u.getCreatedAt());
        r.setRoles(u.getRoles().stream().map(Role::getName).collect(Collectors.toList()));
        u.getRoles().stream()
            .filter(Role::isOrgScoped)
            .findFirst()
            .ifPresent(role -> r.setOrganizationId(role.getOrg().getId()));
        return r;
    }

    private Long primaryOrgId(User user) {
        return user.getRoles().stream()
            .filter(Role::isOrgScoped)
            .findFirst()
            .map(role -> role.getOrg().getId())
            .orElse(null);
    }

    private void revokeChain(RefreshToken reused) {
        Instant now = Instant.now(clock);
        for (RefreshToken token : refreshTokenRepository.findBySessionId(reused.getSession().getId())) {
            if (token.getRevokedAt() == null) {
                token.revokeAt(now);
                refreshTokenRepository.save(token);
            }
        }
        Session session = reused.getSession();
        if (session != null && session.getRevokedAt() == null) {
            session.revokeAt(now);
            sessionRepository.save(session);
        }
    }

    private String generateSlug(String name) {
        String base = name.toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9-]+", "-")
            .replaceAll("-+", "-")
            .replaceAll("^-|-$", "");
        if (base.length() < 3) base = (base + "-org").substring(0, Math.min(100, base.length() + 4));
        if (base.length() > 100) base = base.substring(0, 100);
        if (organizationRepository.existsBySlug(base)) {
            base = base.substring(0, Math.min(95, base.length())) + "-" + TimeProvider.newId().substring(0, 4);
        }
        return base;
    }

    private record IssuedTokens(String accessToken, String refreshToken) {}
}
