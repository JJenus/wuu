package com.surf.tracker.userauth.api;

import com.surf.tracker.main.api.ApiV1;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.surf.tracker.userauth.api.dto.PagedResponse;
import com.surf.tracker.userauth.application.dto.RoleResponse;
import com.surf.tracker.userauth.application.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@ApiV1
@RequestMapping("/roles")
@Tag(name = "Roles", description = "Read-only role catalog; role names are not effective authorization.")
public class RoleController {
    private final AuthService authService;

    public RoleController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping
    @Operation(operationId = "rolesList", summary = "List available tenant roles", description = "Requires roles.read. Zero-based page defaults to 0 and size to 50. The auth service scopes available roles to the current tenant. Returns Cache-Control: private, no-store; no role-write route is exposed here.")
    public ResponseEntity<PagedResponse<RoleResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok().header("Cache-Control", "private, no-store")
            .body(new PagedResponse<>(authService.listRoles(page, size)));
    }
}
