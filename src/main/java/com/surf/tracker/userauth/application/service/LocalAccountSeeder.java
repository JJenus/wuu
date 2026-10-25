package com.surf.tracker.userauth.application.service;

import com.surf.tracker.userauth.domain.entity.Organization;
import com.surf.tracker.userauth.domain.entity.User;
import com.surf.tracker.userauth.infrastructure.repository.*;
import com.surf.tracker.userauth.infrastructure.security.LocalAccountsPolicy;
import com.surf.tracker.userauth.infrastructure.security.PasswordService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "userauth.local-accounts.enabled", havingValue = "true")
public class LocalAccountSeeder {
    private final UserRepository users;
    private final OrganizationRepository organizations;
    private final RoleRepository roles;
    private final AuthService auth;
    private final PasswordService passwords;
    private final JdbcTemplate jdbc;
    private final String userPassword;
    private final String adminPassword;

    public LocalAccountSeeder(UserRepository users, OrganizationRepository organizations,
            RoleRepository roles, AuthService auth, PasswordService passwords, JdbcTemplate jdbc,
            LocalAccountsPolicy policy,
            @Value("${userauth.local-accounts.user-password:}") String userPassword,
            @Value("${userauth.local-accounts.admin-password:}") String adminPassword) {
        if (!policy.isEnabled()) throw new IllegalStateException("Local accounts are disabled");
        this.users = users;
        this.organizations = organizations;
        this.roles = roles;
        this.auth = auth;
        this.passwords = passwords;
        this.jdbc = jdbc;
        this.userPassword = userPassword;
        this.adminPassword = adminPassword;
    }

    @Transactional
    public void seed() {
        // This migrated row always exists. Lock BEFORE checking for accounts/organization:
        // independent startup transactions serialize instead of racing a check-then-insert.
        // Database unique constraints remain the final guard against unrelated writers.
        jdbc.queryForObject("select id from permissions where permission_key = ? for update",
            Long.class, "users.read");
        boolean addUser = !users.existsByEmailIgnoreCase("user@wuu.local");
        boolean addAdmin = !users.existsByEmailIgnoreCase("admin@wuu.local");
        if (!addUser && !addAdmin) return;
        if (addUser) passwords.validateStrength(userPassword);
        if (addAdmin) passwords.validateStrength(adminPassword);
        Organization org = organizations.findBySlug("wuu-local")
            .orElseGet(() -> organizations.saveAndFlush(Organization.create("Wuu local workspace", "wuu-local")));
        auth.seedOrgRoles(org);
        if (addUser) create("user@wuu.local", "Local user", userPassword, "VIEWER", org);
        if (addAdmin) create("admin@wuu.local", "Local administrator", adminPassword, "TENANT_ADMIN", org);
        // Existing accounts are never elevated, unlocked, renamed or given a new password.
    }

    private void create(String email, String name, String password, String role, Organization org) {
        User user = new User();
        user.setEmail(email);
        user.setFirstName(name);
        user.setPasswordHash(passwords.hash(password));
        user.assignRole(roles.findByNameAndOrgId(role, org.getId()).orElseThrow());
        users.saveAndFlush(user);
    }
}
