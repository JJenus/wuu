package com.surf.tracker.userauth.api;

import com.surf.tracker.main.api.ApiV1;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.surf.tracker.userauth.api.dto.PagedResponse;
import com.surf.tracker.userauth.application.dto.OrganizationRequest;
import com.surf.tracker.userauth.application.dto.OrganizationResponse;
import com.surf.tracker.userauth.application.dto.UpdateOrganizationRequest;
import com.surf.tracker.userauth.application.service.OrganizationService;
import com.surf.tracker.userauth.domain.entity.Organization;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@ApiV1
@RequestMapping("/organizations")
@Tag(name = "Organizations", description = "Organization administration. Method security applies in addition to an authenticated bearer session.")
@org.springframework.security.access.prepost.PreAuthorize("hasAuthority('system.admin')")
public class OrganizationController {

    private final OrganizationService organizationService;

    public OrganizationController(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    @GetMapping
    @Operation(operationId = "organizationsList", summary = "List organizations", description = "Requires system.admin. Zero-based page defaults to 0 and size to 20; sorted by createdAt descending. Returns the project pagination envelope.")
    public ResponseEntity<PagedResponse<OrganizationResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(new PagedResponse<>(
            organizationService.listAllPaged(pageable).map(this::toResponse)));
    }

    @GetMapping("/{id}")
    @Operation(operationId = "organizationsGet", summary = "Get an organization", description = "Requires system.admin, or organizations.read with principal.orgId equal to the numeric path ID. The ID does not select a different active tenant for subsequent requests.")
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('system.admin') or (hasAuthority('organizations.read') and principal.orgId == #id)")
    public ResponseEntity<OrganizationResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(toResponse(organizationService.getById(id)));
    }

    @PostMapping
    @Operation(operationId = "organizationsCreate", summary = "Create an organization", description = "Requires system.admin. Validates name and slug; returns the created organization without switching the current session's tenant.")
    @ApiResponse(responseCode = "201", description = "Organization created")
    public ResponseEntity<OrganizationResponse> create(@Valid @RequestBody OrganizationRequest request) {
        Organization created = organizationService.create(request.getName(), request.getSlug());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(created));
    }

    @PutMapping("/{id}")
    @Operation(operationId = "organizationsUpdate", summary = "Update an organization", description = "Requires system.admin, or organizations.write with principal.orgId equal to the numeric path ID. Changes name and slug only.")
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('system.admin') or (hasAuthority('organizations.write') and principal.orgId == #id)")
    public ResponseEntity<OrganizationResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateOrganizationRequest request) {
        Organization updated = organizationService.update(id, request.getName(), request.getSlug());
        return ResponseEntity.ok(toResponse(updated));
    }

    @DeleteMapping("/{id}")
    @Operation(operationId = "organizationsDelete", summary = "Delete an organization", description = "Requires system.admin. Deletes by numeric ID through the organization service. Success is 204 without a response body; persistence constraints may prevent deletion.")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        organizationService.delete(id);
    }

    private OrganizationResponse toResponse(Organization org) {
        return new OrganizationResponse(org.getId(), org.getName(), org.getSlug(), org.getCreatedAt());
    }
}
