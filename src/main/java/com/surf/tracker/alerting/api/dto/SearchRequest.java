package com.surf.tracker.alerting.api.dto;

import com.surf.tracker.alerting.domain.enums.AlertRuleType;
import org.springframework.data.domain.Sort;

public class SearchRequest {
    private Integer page = 0;
    private Integer size = 20;
    private String sortBy = "createdAt";
    private Sort.Direction sortDirection = Sort.Direction.DESC;
    private String search;
    private AlertRuleType ruleType;
    private Boolean enabled;
    private String vehicleId;
    private Boolean active;

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

    public AlertRuleType getRuleType() { return ruleType; }
    public void setRuleType(AlertRuleType ruleType) { this.ruleType = ruleType; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    public String getVehicleId() { return vehicleId; }
    public void setVehicleId(String vehicleId) { this.vehicleId = vehicleId; }

    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
}