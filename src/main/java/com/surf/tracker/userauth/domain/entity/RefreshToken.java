package com.surf.tracker.userauth.domain.entity;

import com.surf.tracker.shared.exception.ValidationException;
import com.surf.tracker.shared.util.TimeProvider;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private Session session;

    @Column(name = "token_hash", nullable = false, unique = true, length = 255)
    private String tokenHash;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rotated_from_id")
    private RefreshToken rotatedFrom;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.EPOCH;

    public static RefreshToken issue(Session session, String tokenHash, Instant expiresAt) {
        if (session == null) {
            throw new ValidationException("RT_SESSION_REQUIRED", "refresh token must reference a session");
        }
        if (tokenHash == null || tokenHash.isBlank()) {
            throw new ValidationException("RT_HASH_REQUIRED", "token hash required");
        }
        if (expiresAt == null) {
            throw new ValidationException("RT_EXPIRY_REQUIRED", "expiry required");
        }
        RefreshToken rt = new RefreshToken();
        rt.session = session;
        rt.tokenHash = tokenHash;
        rt.expiresAt = expiresAt;
        return rt;
    }

    public static RefreshToken rotateFrom(Session session, RefreshToken oldToken,
                                          String newTokenHash, Instant expiresAt) {
        RefreshToken rt = issue(session, newTokenHash, expiresAt);
        rt.rotatedFrom = oldToken;
        return rt;
    }

    public boolean isActive(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }

    public boolean hasBeenRotated() {
        return revokedAt != null;
    }

    public void revoke() {
        this.revokedAt = TimeProvider.now();
    }

    public void revokeAt(Instant when) {
        this.revokedAt = when;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Session getSession() { return session; }
    public void setSession(Session session) { this.session = session; }

    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }

    public RefreshToken getRotatedFrom() { return rotatedFrom; }
    public void setRotatedFrom(RefreshToken rotatedFrom) { this.rotatedFrom = rotatedFrom; }

    public Instant getRevokedAt() { return revokedAt; }
    public void setRevokedAt(Instant revokedAt) { this.revokedAt = revokedAt; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
