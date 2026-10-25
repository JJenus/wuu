package com.surf.tracker.userauth.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class UpdateOrganizationRequest {
    @NotBlank @Size(max = 200)
    private String name;

    @NotBlank
    @Pattern(regexp = "^[a-z0-9][a-z0-9-]{1,98}[a-z0-9]$",
             message = "slug must be 3-100 chars, lowercase alphanumeric and hyphens")
    private String slug;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
}
