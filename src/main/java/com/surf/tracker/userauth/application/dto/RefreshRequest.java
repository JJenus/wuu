package com.surf.tracker.userauth.application.dto;

import jakarta.validation.constraints.NotBlank;

@io.swagger.v3.oas.annotations.media.Schema(description = "Server-issued refresh token, sent as refresh_token in JSON; not an access JWT.")
public class RefreshRequest {
    @NotBlank @jakarta.validation.constraints.Size(max = 128)
    @io.swagger.v3.oas.annotations.media.Schema(accessMode = io.swagger.v3.oas.annotations.media.Schema.AccessMode.WRITE_ONLY)
    private String refreshToken;

    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
}
