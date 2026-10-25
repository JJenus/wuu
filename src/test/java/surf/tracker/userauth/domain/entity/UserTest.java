package com.surf.tracker.userauth.domain.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class UserTest {

    @Test
    void assignRole_addsRole() {
        User user = new User();
        Role role = Role.systemRole("ADMIN", "admin");
        role.setId(1L);

        user.assignRole(role);

        assertThat(user.getRoles()).contains(role);
    }

    @Test
    void assignRole_nullRole_throws() {
        User user = new User();

        assertThatThrownBy(() -> user.assignRole(null))
            .isInstanceOf(com.surf.tracker.shared.exception.ValidationException.class);
    }

    @Test
    void removeRole_removesRole() {
        User user = new User();
        Role role = Role.systemRole("ADMIN", "admin");
        role.setId(1L);
        user.assignRole(role);

        user.removeRole(role);

        assertThat(user.getRoles()).doesNotContain(role);
    }

    @Test
    void lock_setsLockedFlag() {
        User user = new User();
        assertThat(user.isLocked()).isFalse();

        user.lock();

        assertThat(user.isLocked()).isTrue();
    }

    @Test
    void unlock_clearsLockedFlag() {
        User user = new User();
        user.lock();

        user.unlock();

        assertThat(user.isLocked()).isFalse();
    }

    @Test
    void canAuthenticate_enabledAndNotLocked_returnsTrue() {
        User user = new User();
        user.setEnabled(true);

        assertThat(user.canAuthenticate()).isTrue();
    }

    @Test
    void canAuthenticate_locked_returnsFalse() {
        User user = new User();
        user.setEnabled(true);
        user.lock();

        assertThat(user.canAuthenticate()).isFalse();
    }

    @Test
    void canAuthenticate_disabled_returnsFalse() {
        User user = new User();
        user.setEnabled(false);

        assertThat(user.canAuthenticate()).isFalse();
    }

    @Test
    void fullName_bothNames_returnsFullName() {
        User user = new User();
        user.setFirstName("John");
        user.setLastName("Doe");

        assertThat(user.fullName()).isEqualTo("John Doe");
    }

    @Test
    void fullName_firstNameOnly() {
        User user = new User();
        user.setFirstName("John");

        assertThat(user.fullName()).isEqualTo("John");
    }

    @Test
    void fullName_lastNameOnly() {
        User user = new User();
        user.setLastName("Doe");

        assertThat(user.fullName()).isEqualTo("Doe");
    }

    @Test
    void fullName_noNames_returnsEmail() {
        User user = new User();
        user.setEmail("john@example.com");

        assertThat(user.fullName()).isEqualTo("john@example.com");
    }
}
