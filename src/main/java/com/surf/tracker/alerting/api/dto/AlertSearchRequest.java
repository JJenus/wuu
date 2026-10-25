package com.surf.tracker.alerting.api.dto;

import com.surf.tracker.alerting.domain.enums.AlertSeverity;
import com.surf.tracker.alerting.domain.enums.AlertType;
import org.springframework.data.domain.Sort;
import java.time.Instant;

public class AlertSearchRequest {
    private Integer page = 0;
    private Integer size = 20;
    private String sortBy = "triggeredAt";
    private Sort.Direction sortDirection = Sort.Direction.DESC;
    private String search;
    private String vehicleId;
    private String trackerId;
    private AlertType alertType;
    private AlertSeverity severity;
    private Boolean acknowledged;
    private Boolean resolved;
    private Instant startDate;
    private Instant endDate;

    // Getters and Setters
    public Integer getPage() { return page; }
    public void setPage(Integer page) { this.page = page; }

    public Integer getSize() { return size; }
    public void setSize(Integer size) { this.size = size; }

    public String getSortBy() { return sortBy; }
    public void setSortBy(String sortBy) { this.sortBy = sortBy; }

    public Sort.Direction getSortDirection() { return sortDirection; }
    public void setSortDirection(Sort.Direction sortDirection) { this.sortDirection = sortDirection; }

    public String getSearch() { return search; }
    public void setSearch(String search) { this.search = search; }

    public String getVehicleId() { return vehicleId; }
    public void setVehicleId(String vehicleId) { this.vehicleId = vehicleId; }

    public String getTrackerId() { return trackerId; }
    public void setTrackerId(String trackerId) { this.trackerId = trackerId; }

    public AlertType getAlertType() { return alertType; }
    public void setAlertType(AlertType alertType) { this.alertType = alertType; }

    public AlertSeverity getSeverity() { return severity; }
    public void setSeverity(AlertSeverity severity) { this.severity = severity; }

    public Boolean getAcknowledged() { return acknowledged; }
    public void setAcknowledged(Boolean acknowledged) { this.acknowledged = acknowledged; }

    public Boolean getResolved() { return resolved; }
    public void setResolved(Boolean resolved) { this.resolved = resolved; }

    public Instant getStartDate() { return startDate; }
    public void setStartDate(Instant startDate) { this.startDate = startDate; }

    public Instant getEndDate() { return endDate; }
    public void setEndDate(Instant endDate) { this.endDate = endDate; }
}
