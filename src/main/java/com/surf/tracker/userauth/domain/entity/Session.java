package com.surf.tracker.userauth.domain.entity;

import com.surf.tracker.shared.exception.ValidationException;
import com.surf.tracker.shared.util.TimeProvider;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sessions")
public class Session {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "session_uuid", nullable = false, unique = true, length = 36)
    private String sessionUuid = TimeProvider.newId();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "org_id")
    private Long orgId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 255)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.EPOCH;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    public static Session create(User user, String tokenHash, Instant expiresAt) {
        if (user == null) {
            throw new ValidationException("SESSION_USER_REQUIRED", "session must have a user");
        }
        if (tokenHash == null || tokenHash.isBlank()) {
            throw new ValidationException("SESSION_TOKEN_REQUIRED", "session token hash required");
        }
        if (expiresAt == null) {
            throw new ValidationException("SESSION_EXPIRY_REQUIRED", "session expiry required");
        }
        Session s = new Session();
        s.user = user;
        s.tokenHash = tokenHash;
        s.expiresAt = expiresAt;
        return s;
    }

    public boolean isActive(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }

    public void revoke() {
        this.revokedAt = TimeProvider.now();
    }

    public void revokeAt(Instant when) {
        this.revokedAt = when;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSessionUuid() { return sessionUuid; }
    public void setSessionUuid(String sessionUuid) { this.sessionUuid = sessionUuid; }

    public User getUser() { return user; }

    public Long getOrgId() { return orgId; }
    public void setOrgId(Long orgId) { this.orgId = orgId; }
    public void setUser(User user) { this.user = user; }

    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getRevokedAt() { return revokedAt; }
    public void setRevokedAt(Instant revokedAt) { this.revokedAt = revokedAt; }
}
