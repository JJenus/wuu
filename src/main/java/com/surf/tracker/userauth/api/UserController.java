package com.surf.tracker.userauth.api;

import com.surf.tracker.main.api.ApiV1;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

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
@ApiV1
@RequestMapping("/users")
@Tag(name = "Users", description = "Current identity and tenant-scoped user administration. Tenant and actor are derived server-side.")
public class UserController {

    private final AuthService authService;

    public UserController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/me")
    @Operation(operationId = "usersMe", summary = "Get the authenticated user", description = "Any valid active bearer session may read its own profile; users.read is not required. The user ID is derived from the session, not from query parameters.")
    public ResponseEntity<UserResponse> me() {
        Long userId = TenantContext.getCurrentUserId();
        return ResponseEntity.ok(authService.getCurrentUser(userId));
    }

    @GetMapping("/me/access")
    @Operation(operationId = "usersCurrentAccess", summary = "Get current effective permissions", description = "Returns user_id, organization_id, sorted current database permissions and access expiry. Requires an authenticated AuthPrincipal, not users.read. Cache-Control is private, no-store. Role claims are not treated as permissions; this is not an authorization decision for a later request.")
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
    @Operation(operationId = "usersList", summary = "List tenant users", description = "Requires users.read. Zero-based page defaults to 0, size to 50; results are sorted by createdAt descending. A session without an active tenant receives an empty page. Other tenants' users and role memberships are not included; organization_id and roles describe only the active tenant.")
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
    @Operation(operationId = "usersCreate", summary = "Create a user in the active tenant", description = "Requires users.write and an active tenant with an OPERATOR role. Creates an enabled account with that initial tenant role; a missing default role is rejected before creating an account. Validates email/password/profile fields and derives organization from the session. This is not an invitation-email operation, public self-registration or arbitrary tenant/role assignment.")
    @ApiResponse(responseCode = "201", description = "Tenant user created")
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
    @Operation(operationId = "usersGet", summary = "Get a tenant user", description = "Requires users.read. Numeric user ID is resolved within the current tenant through the auth service; caller-supplied tenant overrides are not accepted.")
    public ResponseEntity<UserResponse> getById(@PathVariable Long id) {
        Long orgId = TenantContext.getCurrentOrgId();
        return ResponseEntity.ok(authService.getUserById(id, orgId));
    }

    @PutMapping("/{id}")
    @Operation(operationId = "usersUpdate", summary = "Update a tenant user's profile", description = "Requires users.write. Updates first name, last name and email for the numeric user ID within the current tenant. Does not change password, organization or assigned roles.")
    public ResponseEntity<UserResponse> updateUser(@PathVariable Long id,
                                                   @Valid @RequestBody UpdateUserRequest request) {
        Long orgId = TenantContext.getCurrentOrgId();
        UserResponse updated = authService.updateUserProfile(
            id, orgId, request.getFirstName(), request.getLastName(), request.getEmail());
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    @Operation(operationId = "usersDelete", summary = "Remove tenant membership", description = "Requires users.delete. Removes the numeric user's roles in the active tenant, never their global identity or memberships elsewhere. Revokes all of that account's sessions. The last TENANT_ADMIN cannot be removed. Success returns 204 without a body.")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        Long orgId = TenantContext.getCurrentOrgId();
        authService.deleteUser(id, orgId);
    }

    @PutMapping("/{id}/roles")
    @Operation(operationId = "usersAssignRoles", summary = "Assign tenant roles", description = "Requires users.assign_roles and an active tenant. Accepts role_ids, not client permission claims. The auth service validates the target user and role assignments in the current tenant.")
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
