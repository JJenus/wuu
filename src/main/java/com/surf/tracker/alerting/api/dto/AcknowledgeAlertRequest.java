package com.surf.tracker.alerting.api.dto;

@io.swagger.v3.oas.annotations.media.Schema(description = "An empty object is sufficient. Actor identity is always overwritten from the authenticated principal.", example = "{}")
public class AcknowledgeAlertRequest {

    // Legacy input retained for compatibility; HTTP always replaces it with the
    // authenticated principal's database user ID before invoking the service.
    @io.swagger.v3.oas.annotations.media.Schema(description = "Ignored on HTTP input; server derives the actor from the authenticated database user.", deprecated = true)
    private String acknowledgedBy;

    public String getAcknowledgedBy() { return acknowledgedBy; }
    public void setAcknowledgedBy(String acknowledgedBy) { this.acknowledgedBy = acknowledgedBy; }
}
