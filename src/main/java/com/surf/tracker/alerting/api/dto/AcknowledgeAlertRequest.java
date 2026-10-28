package com.surf.tracker.alerting.api.dto;

import jakarta.validation.constraints.NotEmpty;

public class AcknowledgeAlertRequest {

    @NotEmpty(message = "Acknowledged by is required")
    private String acknowledgedBy;

    public String getAcknowledgedBy() { return acknowledgedBy; }
    public void setAcknowledgedBy(String acknowledgedBy) { this.acknowledgedBy = acknowledgedBy; }
}
