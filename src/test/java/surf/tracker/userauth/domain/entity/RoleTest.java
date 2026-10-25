package com.surf.tracker.userauth.domain.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class RoleTest {

    @Test
    void systemRole_isSystemRole_returnsTrue() {
        Role role = Role.systemRole("SUPER_ADMIN", "system admin");

        assertThat(role.isSystemRole()).isTrue();
        assertThat(role.isOrgScoped()).isFalse();
        assertThat(role.getOrg()).isNull();
    }

    @Test
    void orgRole_isOrgScoped() {
        Organization org = Organization.create("Acme Corp", "acme-corp");
        org.setId(1L);
        Role role = Role.orgRole(org, "OPERATOR", "operator");

        assertThat(role.isOrgScoped()).isTrue();
        assertThat(role.isSystemRole()).isFalse();
        assertThat(role.getOrg()).isEqualTo(org);
    }

    @Test
    void addPermission_addsPermission() {
        Role role = Role.systemRole("ADMIN", "admin");
        Permission p = Permission.of("users.read", "read users");

        role.addPermission(p);

        assertThat(role.getPermissions()).contains(p);
    }

    @Test
    void removePermission_removesPermission() {
        Role role = Role.systemRole("ADMIN", "admin");
        Permission p = Permission.of("users.read", "read users");
        role.addPermission(p);

        role.removePermission(p);

        assertThat(role.getPermissions()).doesNotContain(p);
    }

    @Test
    void appliesToOrg_systemRole_alwaysTrue() {
        Role role = Role.systemRole("SUPER_ADMIN", "admin");
        role.setId(1L);

        assertThat(role.appliesToOrg(1L)).isTrue();
        assertThat(role.appliesToOrg(99L)).isTrue();
    }

    @Test
    void appliesToOrg_orgRole_matchesCorrectOrg() {
        Organization org = Organization.create("Acme", "acme");
        org.setId(5L);
        Role role = Role.orgRole(org, "ADMIN", "admin");
        role.setId(1L);

        assertThat(role.appliesToOrg(5L)).isTrue();
        assertThat(role.appliesToOrg(99L)).isFalse();
    }

    @Test
    void setName_null_throws() {
        Role role = new Role();

        assertThatThrownBy(() -> role.setName(null))
            .isInstanceOf(com.surf.tracker.shared.exception.ValidationException.class);
    }

    @Test
    void setName_blank_throws() {
        Role role = new Role();

        assertThatThrownBy(() -> role.setName("   "))
            .isInstanceOf(com.surf.tracker.shared.exception.ValidationException.class);
    }
}
