package com.surf.tracker.userauth.application.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class LoginRequest {
    @NotBlank @Email @jakarta.validation.constraints.Size(max = 255)
    private String email;

    @NotBlank @jakarta.validation.constraints.Size(max = 128)
    private String password;

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
