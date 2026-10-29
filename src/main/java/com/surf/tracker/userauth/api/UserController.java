package com.surf.tracker.userauth.api;

import com.surf.tracker.userauth.api.dto.PagedResponse;
import com.surf.tracker.userauth.application.dto.*;
import com.surf.tracker.userauth.application.service.AuthService;
import com.surf.tracker.shared.security.TenantContext;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.surf.tracker.userauth.infrastructure.security.AuthPrincipal;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final AuthService authService;

    public UserController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> me() {
        Long userId = TenantContext.getCurrentUserId();
        return ResponseEntity.ok(authService.getCurrentUser(userId));
    }

    @GetMapping("/me/access")
    public ResponseEntity<CurrentAccessResponse> currentAccess(@AuthenticationPrincipal AuthPrincipal principal) {
        if (principal == null) {
            throw new org.springframework.security.access.AccessDeniedException("Authenticated identity required");
        }
        // The filter resolved current database roles, membership and session validity
        // for this request. JWT role claims never become effective authority here.
        return ResponseEntity.ok().header("Cache-Control", "private, no-store")
            .body(new CurrentAccessResponse(principal.userId(), principal.orgId(),
                principal.permissions().stream().sorted().toList(), principal.accessExpiresAt()));
    }

    @GetMapping
    public ResponseEntity<PagedResponse<UserResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Long orgId = TenantContext.getCurrentOrgId();
        if (orgId == null) {
            return ResponseEntity.ok(new PagedResponse<>(
                Page.empty(PageRequest.of(page, size))));
        }
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(new PagedResponse<>(
            authService.listUsersInOrgPaged(orgId, pageable)));
    }

    @PostMapping
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        Long orgId = TenantContext.getCurrentOrgId();
        UserResponse created = authService.createUser(
            request.getEmail(), request.getPassword(),
            request.getFirstName(), request.getLastName(),
            requireOrg(orgId)
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getById(@PathVariable Long id) {
        Long orgId = TenantContext.getCurrentOrgId();
        return ResponseEntity.ok(authService.getUserById(id, orgId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserResponse> updateUser(@PathVariable Long id,
                                                   @Valid @RequestBody UpdateUserRequest request) {
        Long orgId = TenantContext.getCurrentOrgId();
        UserResponse updated = authService.updateUserProfile(
            id, orgId, request.getFirstName(), request.getLastName(), request.getEmail());
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        Long orgId = TenantContext.getCurrentOrgId();
        authService.deleteUser(id, orgId);
    }

    @PutMapping("/{id}/roles")
    public ResponseEntity<UserResponse> assignRoles(@PathVariable Long id,
                                                    @Valid @RequestBody AssignRolesRequest request) {
        Long orgId = TenantContext.getCurrentOrgId();
        UserResponse updated = authService.assignRoles(id, request.getRoleIds(),
            requireOrg(orgId));
        return ResponseEntity.ok(updated);
    }

    private Long requireOrg(Long orgId) {
        if (orgId == null) throw new org.springframework.security.access.AccessDeniedException("Tenant required");
        return orgId;
    }
}
