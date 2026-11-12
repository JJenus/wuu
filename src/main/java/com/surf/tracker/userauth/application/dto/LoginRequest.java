package com.surf.tracker.userauth.application.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@io.swagger.v3.oas.annotations.media.Schema(description = "Email/password credentials. Tokens are returned only after server-side authentication.")
public class LoginRequest {
    @NotBlank @Email @jakarta.validation.constraints.Size(max = 255)
    @io.swagger.v3.oas.annotations.media.Schema(example = "driver@example.com")
    private String email;

    @NotBlank @jakarta.validation.constraints.Size(max = 128)
    @io.swagger.v3.oas.annotations.media.Schema(description = "Account password; never log or cache credentials.", accessMode = io.swagger.v3.oas.annotations.media.Schema.AccessMode.WRITE_ONLY)
    private String password;

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
