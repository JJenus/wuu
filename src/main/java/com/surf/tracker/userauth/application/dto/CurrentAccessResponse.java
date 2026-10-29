package com.surf.tracker.userauth.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;

/** Request-time authorization snapshot, not a durable capability or tenant-selection token. */
public record CurrentAccessResponse(@JsonProperty("user_id") Long userId,
                                    @JsonProperty("organization_id") Long organizationId,
                                    List<String> permissions,
                                    @JsonProperty("access_expires_at") Instant accessExpiresAt) {
    public CurrentAccessResponse {
        permissions = List.copyOf(permissions);
    }
}
