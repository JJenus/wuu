package com.surf.tracker.userauth.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.Duration;

@ConfigurationProperties(prefix = "userauth.jwt")
public class JwtConfig {

    private String secret;
    private Duration accessTokenExpiry = Duration.ofMinutes(15);
    private Duration refreshTokenExpiry = Duration.ofHours(168);
    private String issuer = "tracker-auth";
    private String audience = "tracking-engine";

    public String getAudience() { return audience; }
    public void setAudience(String audience) { this.audience = audience; }

    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }

    public Duration getAccessTokenExpiry() { return accessTokenExpiry; }
    public void setAccessTokenExpiry(Duration accessTokenExpiry) { this.accessTokenExpiry = accessTokenExpiry; }

    public Duration getRefreshTokenExpiry() { return refreshTokenExpiry; }
    public void setRefreshTokenExpiry(Duration refreshTokenExpiry) { this.refreshTokenExpiry = refreshTokenExpiry; }

    public String getIssuer() { return issuer; }
    public void setIssuer(String issuer) { this.issuer = issuer; }
}
