package com.surf.tracker.userauth.application.dto;

import jakarta.validation.constraints.NotBlank;

public class RefreshRequest {
    @NotBlank @jakarta.validation.constraints.Size(max = 128)
    private String refreshToken;

    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
}
