package com.surf.tracker.userauth.api;

import com.surf.tracker.userauth.api.dto.PagedResponse;
import com.surf.tracker.userauth.application.dto.RoleResponse;
import com.surf.tracker.userauth.application.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/roles")
public class RoleController {
    private final AuthService authService;

    public RoleController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping
    public ResponseEntity<PagedResponse<RoleResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok().header("Cache-Control", "private, no-store")
            .body(new PagedResponse<>(authService.listRoles(page, size)));
    }
}
