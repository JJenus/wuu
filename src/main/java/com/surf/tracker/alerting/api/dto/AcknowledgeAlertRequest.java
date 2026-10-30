package com.surf.tracker.alerting.api.dto;

public class AcknowledgeAlertRequest {

    // Legacy input retained for compatibility; HTTP always replaces it with the
    // authenticated principal's database user ID before invoking the service.
    private String acknowledgedBy;

    public String getAcknowledgedBy() { return acknowledgedBy; }
    public void setAcknowledgedBy(String acknowledgedBy) { this.acknowledgedBy = acknowledgedBy; }
}
