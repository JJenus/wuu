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

        assertThatCode(() -> service.validateStrength("12345678")).doesNotThrowAnyException();
    }

    @Test
    void hash_eightCharacters_matchesWithoutChangingBcryptLimit() {
        var service = new PasswordService(4);
        String hash = service.hash("eight123");
        assertThat(service.matches("eight123", hash)).isTrue();
        assertThatThrownBy(() -> service.hash("é".repeat(37)))
                .isInstanceOf(com.surf.tracker.shared.exception.ValidationException.class)
                .hasMessageContaining("72 UTF-8 bytes");
    }

    @Test
    void validateRequestPasswords_eightAcceptedSevenRejected_acrossAllCreationBoundaries() {
        try (var factory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            var registration = new com.surf.tracker.userauth.application.dto.RegisterRequest();
            registration.setPassword("eight123");
            var creation = new com.surf.tracker.userauth.application.dto.CreateUserRequest();
            creation.setPassword("eight123");
            var change = new com.surf.tracker.userauth.api.AuthController.ChangePasswordRequest();
            change.setNewPassword("eight123");
            assertThat(validator.validateProperty(registration, "password")).isEmpty();
            assertThat(validator.validateProperty(creation, "password")).isEmpty();
            assertThat(validator.validateProperty(change, "newPassword")).isEmpty();
            registration.setPassword("seven12");
            creation.setPassword("seven12");
            change.setNewPassword("seven12");
            assertThat(validator.validateProperty(registration, "password")).isNotEmpty();
            assertThat(validator.validateProperty(creation, "password")).isNotEmpty();
            assertThat(validator.validateProperty(change, "newPassword")).isNotEmpty();
        }
    }

    @Test
    void validateStrength_belowMinLength_throws() {
        PasswordService service = new PasswordService(10);

        assertThatThrownBy(() -> service.validateStrength("1234567"))
            .isInstanceOf(com.surf.tracker.shared.exception.ValidationException.class);
    }
}
