package com.surf.tracker.userauth.infrastructure.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

import com.surf.tracker.shared.exception.ValidationException;

@Service
public class PasswordService {

    public static final int MIN_PASSWORD_LENGTH = 12;

    private final BCryptPasswordEncoder encoder;
    private final String dummyHash;

    public PasswordService(@Value("${userauth.password.bcrypt-strength:12}") int strength) {
        if (strength < 4 || strength > 31) {
            throw new IllegalArgumentException("bcrypt strength must be between 4 and 31");
        }
        this.encoder = new BCryptPasswordEncoder(strength);
        this.dummyHash = encoder.encode("dummy-password-not-an-account");
    }

    public void validateStrength(String rawPassword) {
        if (rawPassword == null) {
            throw new ValidationException("PASSWORD_REQUIRED", "password is required");
        }
        if (rawPassword.codePointCount(0, rawPassword.length()) < MIN_PASSWORD_LENGTH) {
            throw new ValidationException("PASSWORD_TOO_SHORT",
                "password must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }
        if (rawPassword.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ValidationException("PASSWORD_TOO_LONG", "password must not exceed 72 UTF-8 bytes");
        }
    }

    public String hash(String rawPassword) {
        validateStrength(rawPassword);
        return encoder.encode(rawPassword);
    }

    public boolean matches(String rawPassword, String storedHash) {
        if (rawPassword == null || storedHash == null || rawPassword.getBytes(StandardCharsets.UTF_8).length > 72) return false;
        return encoder.matches(rawPassword, storedHash);
    }

    public void checkUnknownAccount(String password) {
        matches(password, dummyHash);
    }
}
