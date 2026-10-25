package com.surf.tracker.userauth.api;

import com.surf.tracker.shared.exception.DomainException;
import com.surf.tracker.userauth.application.dto.*;
import com.surf.tracker.userauth.application.service.AuthService;
import com.surf.tracker.userauth.infrastructure.security.AuthException;
import com.surf.tracker.userauth.infrastructure.security.SecurityException;
import com.surf.tracker.shared.security.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request,
                                                    HttpServletRequest http) {
        RegisterResponse response = authService.register(request, clientIp(http), userAgent(http));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                              HttpServletRequest http) {
        LoginResponse response = authService.login(request.getEmail(), request.getPassword(),
            clientIp(http), userAgent(http));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(@Valid @RequestBody RefreshRequest request,
                                                HttpServletRequest http) {
        LoginResponse response = authService.refresh(request.getRefreshToken(),
            clientIp(http), userAgent(http));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest http) {
        Long userId = TenantContext.getCurrentUserId();
        authService.logout(userId, clientIp(http), userAgent(http));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest body,
                                              HttpServletRequest http) {
        Long userId = TenantContext.getCurrentUserId();
        authService.changePassword(userId, body.getOldPassword(), body.getNewPassword(),
            clientIp(http), userAgent(http));
        return ResponseEntity.noContent().build();
    }

    private String clientIp(HttpServletRequest request) {
        return request.getRemoteAddr();
    }

    private String userAgent(HttpServletRequest request) {
        String ua = request.getHeader("User-Agent");
        return ua == null ? null : ua.substring(0, Math.min(ua.length(), 500));
    }

    public static class ChangePasswordRequest {
        @jakarta.validation.constraints.NotBlank
        @jakarta.validation.constraints.Size(max = 128)
        private String oldPassword;
        @jakarta.validation.constraints.NotBlank
        @jakarta.validation.constraints.Size(min = 12, max = 72)
        private String newPassword;
        public String getOldPassword() { return oldPassword; }
        public void setOldPassword(String oldPassword) { this.oldPassword = oldPassword; }
        public String getNewPassword() { return newPassword; }
        public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
    }
}
