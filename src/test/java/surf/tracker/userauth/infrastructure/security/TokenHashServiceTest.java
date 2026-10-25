package com.surf.tracker.userauth.infrastructure.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class TokenHashServiceTest {

    private final TokenHashService service = new TokenHashService();

    @Test
    void hash_sameInput_returnsSameHash() {
        String hash1 = service.hash("token-123");
        String hash2 = service.hash("token-123");

        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    void hash_differentInput_returnsDifferentHash() {
        String hash1 = service.hash("token-123");
        String hash2 = service.hash("token-456");

        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    void hash_returnsHexSha256() {
        String hash = service.hash("any-token");

        assertThat(hash).hasSize(64);
        assertThat(hash).matches("[0-9a-f]+");
    }

    @Test
    void hash_null_throws() {
        assertThatThrownBy(() -> service.hash(null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void generateOpaqueToken_returnsNonBlank() {
        String token = service.generateOpaqueToken();

        assertThat(token).isNotBlank();
    }

    @Test
    void generateOpaqueToken_uniqueEachCall() {
        String t1 = service.generateOpaqueToken();
        String t2 = service.generateOpaqueToken();

        assertThat(t1).isNotEqualTo(t2);
    }

    @Test
    void hash_ofGeneratedToken_isStable() {
        String token = service.generateOpaqueToken();
        String hash1 = service.hash(token);
        String hash2 = service.hash(token);

        assertThat(hash1).isEqualTo(hash2);
    }
}
