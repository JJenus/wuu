package com.surf.tracker.userauth.domain.entity;

import com.surf.tracker.shared.exception.ValidationException;
import com.surf.tracker.shared.util.TimeProvider;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "roles")
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "org_id")
    private Organization org;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.EPOCH;

    @ManyToMany(fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(
        name = "role_permissions",
        joinColumns = @JoinColumn(name = "role_id"),
        inverseJoinColumns = @JoinColumn(name = "permission_id")
    )
    private Set<Permission> permissions = new HashSet<>();

    public static Role systemRole(String name, String description) {
        Role r = new Role();
        r.setName(name);
        r.setDescription(description);
        return r;
    }

    public static Role orgRole(Organization org, String name, String description) {
        Role r = new Role();
        r.setOrg(org);
        r.setName(name);
        r.setDescription(description);
        return r;
    }

    public void addPermission(Permission permission) {
        this.permissions.add(permission);
    }

    public void removePermission(Permission permission) {
        this.permissions.remove(permission);
    }

    public boolean isSystemRole() {
        return org == null;
    }

    public boolean isOrgScoped() {
        return org != null;
    }

    public boolean appliesToOrg(Long orgId) {
        if (isSystemRole()) return true;
        return org != null && org.getId().equals(orgId);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) {
        if (name == null || name.isBlank()) {
            throw new ValidationException("ROLE_NAME_REQUIRED", "role name must not be blank");
        }
        this.name = name;
    }

    public Organization getOrg() { return org; }
    public void setOrg(Organization org) { this.org = org; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Set<Permission> getPermissions() { return permissions; }
    public void setPermissions(Set<Permission> permissions) { this.permissions = permissions; }
}
