package com.surf.tracker.userauth.application.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public class AssignRolesRequest {
    @NotNull
    private List<Long> roleIds;

    private Long organizationId;

    public List<Long> getRoleIds() { return roleIds; }
    public void setRoleIds(List<Long> roleIds) { this.roleIds = roleIds; }

    public Long getOrganizationId() { return organizationId; }
    public void setOrganizationId(Long organizationId) { this.organizationId = organizationId; }
}
