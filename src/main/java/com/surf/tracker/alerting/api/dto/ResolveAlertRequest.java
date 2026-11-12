package com.surf.tracker.alerting.api.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

@io.swagger.v3.oas.annotations.media.Schema(description = "Resolution notes with server-derived audit actor.", example = "{\"resolution_notes\":\"Reviewed the event and confirmed the vehicle is safe.\"}")
public class ResolveAlertRequest {

    // Legacy input retained for compatibility; HTTP derives this from identity.
    @io.swagger.v3.oas.annotations.media.Schema(description = "Ignored on HTTP input; server derives the actor from the authenticated database user.", deprecated = true)
    private String resolvedBy;

    @NotEmpty(message = "Resolution notes are required")
    @Size(min = 10, max = 1000, message = "Resolution notes must be between 10 and 1000 characters")
    private String resolutionNotes;

    public String getResolvedBy() { return resolvedBy; }
    public void setResolvedBy(String resolvedBy) { this.resolvedBy = resolvedBy; }

    public String getResolutionNotes() { return resolutionNotes; }
    public void setResolutionNotes(String resolutionNotes) { this.resolutionNotes = resolutionNotes; }
}
