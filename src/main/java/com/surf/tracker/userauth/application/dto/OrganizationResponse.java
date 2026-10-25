package com.surf.tracker.userauth.application.dto;

import java.time.Instant;

public record OrganizationResponse(
        Long id,
        String name,
        String slug,
        Instant createdAt
) {
}
