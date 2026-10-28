package com.surf.tracker.userauth.infrastructure.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class PasswordServiceTest {

    @Test
    void hash_validPassword_returnsHashedValue() {
        PasswordService service = new PasswordService(10);
        String raw = "validPassword123";

        String hash = service.hash(raw);

        assertThat(hash).isNotEqualTo(raw);
        assertThat(hash).startsWith("$2a$");
        assertThat(hash.length()).isGreaterThan(50);
    }

    @Test
    void hash_samePassword_producesDifferentHashes() {
        PasswordService service = new PasswordService(10);

        String hash1 = service.hash("test-password-123");
        String hash2 = service.hash("test-password-123");

        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    void hash_tooShort_throws() {
        PasswordService service = new PasswordService(10);

        assertThatThrownBy(() -> service.hash("short"))
            .isInstanceOf(com.surf.tracker.shared.exception.ValidationException.class);
    }

    @Test
    void hash_null_throws() {
        PasswordService service = new PasswordService(10);

        assertThatThrownBy(() -> service.hash(null))
            .isInstanceOf(com.surf.tracker.shared.exception.ValidationException.class);
    }

    @Test
    void matches_correctPassword_returnsTrue() {
        PasswordService service = new PasswordService(10);
        String hash = service.hash("correctPassword");

        assertThat(service.matches("correctPassword", hash)).isTrue();
    }

    @Test
    void matches_incorrectPassword_returnsFalse() {
        PasswordService service = new PasswordService(10);
        String hash = service.hash("correctPassword");

        assertThat(service.matches("wrongPassword", hash)).isFalse();
    }

    @Test
    void matches_nullInputs_returnsFalse() {
        PasswordService service = new PasswordService(10);

        assertThat(service.matches(null, "hash")).isFalse();
        assertThat(service.matches("password", null)).isFalse();
        assertThat(service.matches(null, null)).isFalse();
    }

    @Test
    void constructor_invalidStrength_throws() {
        assertThatThrownBy(() -> new PasswordService(2))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PasswordService(40))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validateStrength_minLength_passes() {
        PasswordService service = new PasswordService(10);

        assertThatCode(() -> service.validateStrength("123456789012")).doesNotThrowAnyException();
    }

    @Test
    void validateStrength_belowMinLength_throws() {
        PasswordService service = new PasswordService(10);

        assertThatThrownBy(() -> service.validateStrength("1234567"))
            .isInstanceOf(com.surf.tracker.shared.exception.ValidationException.class);
    }
}
