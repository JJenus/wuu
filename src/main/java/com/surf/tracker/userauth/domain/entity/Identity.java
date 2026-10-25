package com.surf.tracker.userauth.domain.entity;

import com.surf.tracker.shared.util.TimeProvider;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "identities",
       uniqueConstraints = @UniqueConstraint(name = "uk_identities_provider_uid",
                                             columnNames = {"provider", "provider_uid"}))
public class Identity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "provider", nullable = false, length = 50)
    private String provider;

    @Column(name = "provider_uid", nullable = false, length = 255)
    private String providerUid;

    @Column(name = "provider_access_token", length = 2000)
    private String providerAccessToken;

    @Column(name = "provider_refresh_token", length = 2000)
    private String providerRefreshToken;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.EPOCH;

    public static Identity link(User user, String provider, String providerUid) {
        Identity identity = new Identity();
        identity.user = user;
        identity.provider = provider;
        identity.providerUid = providerUid;
        return identity;
    }

    public void storeProviderTokens(String accessToken, String refreshToken) {
        this.providerAccessToken = accessToken;
        this.providerRefreshToken = refreshToken;
    }

    public void clearProviderTokens() {
        this.providerAccessToken = null;
        this.providerRefreshToken = null;
    }

    public boolean matches(String provider, String providerUid) {
        return this.provider.equals(provider) && this.providerUid.equals(providerUid);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getProviderUid() { return providerUid; }
    public void setProviderUid(String providerUid) { this.providerUid = providerUid; }

    public String getProviderAccessToken() { return providerAccessToken; }
    public void setProviderAccessToken(String providerAccessToken) {
        this.providerAccessToken = providerAccessToken;
    }

    public String getProviderRefreshToken() { return providerRefreshToken; }
    public void setProviderRefreshToken(String providerRefreshToken) {
        this.providerRefreshToken = providerRefreshToken;
    }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
