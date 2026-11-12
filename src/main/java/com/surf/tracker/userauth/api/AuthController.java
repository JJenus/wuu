package com.surf.tracker.userauth.api;

import com.surf.tracker.main.api.ApiV1;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.headers.Header;

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
@ApiV1
@RequestMapping("/auth")
@Tag(name = "Authentication", description = "Session-backed JWT authentication. POSTs are rate-limited per remote IP; 429 includes Retry-After: 60.")
@ApiResponses({@ApiResponse(responseCode = "400", description = "Malformed or invalid request", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(responseCode = "429", description = "Per-instance IP rate limit exceeded", headers = @Header(name = "Retry-After", description = "Retry delay in seconds", schema = @Schema(type = "integer", example = "60")), content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))})
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @SecurityRequirements
    @Operation(operationId = "authRegister", summary = "Register an account", description = "Available only when userauth.registration-enabled=true; disabled by default. No bearer token is required when enabled. Records remote IP and bounded User-Agent for audit; client-selected tenant/role claims do not grant permissions.")
    @ApiResponse(responseCode = "201", description = "Registration created", content = @Content(schema = @Schema(implementation = RegisterResponse.class)))
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request,
                                                    HttpServletRequest http) {
        RegisterResponse response = authService.register(request, clientIp(http), userAgent(http));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    @SecurityRequirements
    @Operation(operationId = "authLogin", summary = "Create an authenticated session", description = "Accepts email/password and returns access and refresh tokens. Send the access token as Authorization: Bearer <token>. Subsequent requests recheck active session, database permissions and tenant membership; JWT role claims alone grant no authority.")
    @ApiResponse(responseCode = "200", description = "Session and tokens issued", content = @Content(schema = @Schema(implementation = LoginResponse.class)))
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                              HttpServletRequest http) {
        LoginResponse response = authService.login(request.getEmail(), request.getPassword(),
            clientIp(http), userAgent(http));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    @SecurityRequirements
    @Operation(operationId = "authRefresh", summary = "Refresh session tokens", description = "Accepts refresh_token in the JSON body, not a bearer access token. Returns the current token response after server-side refresh-token/session validation. Invalid or revoked refresh credentials are rejected.")
    @ApiResponse(responseCode = "200", description = "Token response", content = @Content(schema = @Schema(implementation = LoginResponse.class)))
    public ResponseEntity<LoginResponse> refresh(@Valid @RequestBody RefreshRequest request,
                                                HttpServletRequest http) {
        LoginResponse response = authService.refresh(request.getRefreshToken(),
            clientIp(http), userAgent(http));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    @Operation(operationId = "authLogout", summary = "Revoke account sessions", description = "Requires a valid bearer session. Revokes the authenticated user's sessions through the auth service; does not accept a caller-selected user ID. Success has no response body.")
    @ApiResponse(responseCode = "204", description = "Sessions revoked", content = @Content)
    public ResponseEntity<Void> logout(HttpServletRequest http) {
        Long userId = TenantContext.getCurrentUserId();
        authService.logout(userId, clientIp(http), userAgent(http));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/change-password")
    @Operation(operationId = "authChangePassword", summary = "Change the authenticated user's password", description = "Requires the current password and a new password of 8–72 characters. User identity comes from the authenticated session. Applies the auth service's session-revocation policy; success has no response body.")
    @ApiResponse(responseCode = "204", description = "Password changed", content = @Content)
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
        @jakarta.validation.constraints.Size(min = com.surf.tracker.userauth.infrastructure.security.PasswordService.MIN_PASSWORD_LENGTH, max = 72)
        private String newPassword;
        public String getOldPassword() { return oldPassword; }
        public void setOldPassword(String oldPassword) { this.oldPassword = oldPassword; }
        public String getNewPassword() { return newPassword; }
        public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
    }
}
