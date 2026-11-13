package com.surf.tracker.userauth.application.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Names only. Email, roles, tenant, password and account status are never self-profile inputs. */
public record UpdateOwnProfileRequest(@NotNull @Size(max = 100) String firstName,
                                      @NotNull @Size(max = 100) String lastName) {
    @JsonAnySetter
    public void rejectUnsupportedField(String name, JsonNode ignored) {
        // Unknown HTTP JSON is a dynamic boundary; it is rejected, never stored as domain data.
        throw new IllegalArgumentException("Unsupported self-profile field");
    }
}
