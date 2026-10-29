package com.surf.tracker.userauth.application.dto;

public class RegisterResponse {
    private Long userId;
    private Long organizationId;
    private String accessToken;
    private String refreshToken;
    private long expiresIn;
    private long refreshExpiresIn;

    public RegisterResponse() {}

    public RegisterResponse(Long userId, Long organizationId, String accessToken,
                            String refreshToken, long expiresIn, long refreshExpiresIn) {
        this.userId = userId;
        this.organizationId = organizationId;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.expiresIn = expiresIn;
        this.refreshExpiresIn = refreshExpiresIn;
    }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getOrganizationId() { return organizationId; }
    public void setOrganizationId(Long organizationId) { this.organizationId = organizationId; }

    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }

    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }

    public long getExpiresIn() { return expiresIn; }
    public void setExpiresIn(long expiresIn) { this.expiresIn = expiresIn; }

    public long getRefreshExpiresIn() { return refreshExpiresIn; }
    public void setRefreshExpiresIn(long refreshExpiresIn) { this.refreshExpiresIn = refreshExpiresIn; }
}
