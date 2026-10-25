package com.surf.tracker.alerting.api.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public class ResolveAlertRequest {

    @NotEmpty(message = "Resolved by is required")
    private String resolvedBy;

    @NotEmpty(message = "Resolution notes are required")
    @Size(min = 10, max = 1000, message = "Resolution notes must be between 10 and 1000 characters")
    private String resolutionNotes;

    public String getResolvedBy() { return resolvedBy; }
    public void setResolvedBy(String resolvedBy) { this.resolvedBy = resolvedBy; }

    public String getResolutionNotes() { return resolutionNotes; }
    public void setResolutionNotes(String resolutionNotes) { this.resolutionNotes = resolutionNotes; }
}
