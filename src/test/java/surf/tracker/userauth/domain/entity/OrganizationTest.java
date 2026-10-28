package com.surf.tracker.userauth.domain.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class OrganizationTest {

    @Test
    void create_setsNameAndSlug() {
        Organization org = Organization.create("Acme Corp", "acme-corp");

        assertThat(org.getName()).isEqualTo("Acme Corp");
        assertThat(org.getSlug()).isEqualTo("acme-corp");
    }

    @Test
    void setSlug_invalidPattern_throws() {
        Organization org = new Organization();

        assertThatThrownBy(() -> org.setSlug("Invalid Slug!"))
            .isInstanceOf(com.surf.tracker.shared.exception.ValidationException.class);
    }

    @Test
    void setSlug_validSlugs() {
        Organization org = new Organization();

        assertThatCode(() -> org.setSlug("acme")).doesNotThrowAnyException();
        assertThatCode(() -> org.setSlug("acme-corp")).doesNotThrowAnyException();
        assertThatCode(() -> org.setSlug("acme-corp-123")).doesNotThrowAnyException();
        assertThatCode(() -> org.setSlug("a1b")).doesNotThrowAnyException();
    }

    @Test
    void setSlug_null_throws() {
        Organization org = new Organization();

        assertThatThrownBy(() -> org.setSlug(null))
            .isInstanceOf(com.surf.tracker.shared.exception.ValidationException.class);
    }

    @Test
    void setName_null_throws() {
        Organization org = new Organization();

        assertThatThrownBy(() -> org.setName(null))
            .isInstanceOf(com.surf.tracker.shared.exception.ValidationException.class);
    }

    @Test
    void setName_blank_throws() {
        Organization org = new Organization();

        assertThatThrownBy(() -> org.setName("   "))
            .isInstanceOf(com.surf.tracker.shared.exception.ValidationException.class);
    }

    @Test
    void rename_validName() {
        Organization org = Organization.create("Old Name", "old-name");

        org.rename("New Name");

        assertThat(org.getName()).isEqualTo("New Name");
    }

    @Test
    void rename_null_throws() {
        Organization org = Organization.create("Old", "old");

        assertThatThrownBy(() -> org.rename(null))
            .isInstanceOf(com.surf.tracker.shared.exception.ValidationException.class);
    }

    @Test
    void addRole_addsRole() {
        Organization org = Organization.create("Acme", "acme");
        Role role = Role.orgRole(org, "ADMIN", "admin");

        org.addRole(role);

        assertThat(org.getRoles()).contains(role);
    }
}
