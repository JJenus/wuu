package com.surf.tracker.userauth.application.dto;

import java.util.List;

/** Tenant-owned role catalog entry; reading this entry grants no authority. */
public record RoleResponse(Long id, String name, String description, List<String> permissions) {
    public RoleResponse {
        permissions = List.copyOf(permissions);
    }
}
