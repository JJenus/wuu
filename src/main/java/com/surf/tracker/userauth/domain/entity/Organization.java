package com.surf.tracker.userauth.domain.entity;

import com.surf.tracker.shared.exception.ValidationException;
import com.surf.tracker.shared.util.TimeProvider;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

@Entity
@Table(name = "organizations")
public class Organization {

    private static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9-]{1,98}[a-z0-9]$");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "slug", nullable = false, unique = true, length = 100)
    private String slug;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.EPOCH;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.EPOCH;

    @OneToMany(mappedBy = "org", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Role> roles = new HashSet<>();

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = TimeProvider.now();
    }

    public static Organization create(String name, String slug) {
        Organization org = new Organization();
        org.setName(name);
        org.setSlug(slug);
        return org;
    }

    public void rename(String newName) {
        if (newName == null || newName.isBlank()) {
            throw new ValidationException("ORG_NAME_REQUIRED", "organization name must not be blank");
        }
        this.name = newName;
    }

    public void changeSlug(String newSlug) {
        if (newSlug == null || !SLUG_PATTERN.matcher(newSlug).matches()) {
            throw new ValidationException("ORG_SLUG_INVALID",
                "slug must be 3-100 chars, lowercase alphanumeric and hyphens");
        }
        this.slug = newSlug;
    }

    public void addRole(Role role) {
        this.roles.add(role);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) {
        if (name == null || name.isBlank()) {
            throw new ValidationException("ORG_NAME_REQUIRED", "organization name must not be blank");
        }
        this.name = name;
    }

    public String getSlug() { return slug; }
    public void setSlug(String slug) {
        if (slug == null || !SLUG_PATTERN.matcher(slug).matches()) {
            throw new ValidationException("ORG_SLUG_INVALID",
                "slug must be 3-100 chars, lowercase alphanumeric and hyphens");
        }
        this.slug = slug;
    }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Set<Role> getRoles() { return roles; }
    public void setRoles(Set<Role> roles) { this.roles = roles; }
}
