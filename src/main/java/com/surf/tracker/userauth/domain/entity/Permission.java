package com.surf.tracker.userauth.domain.entity;

import com.surf.tracker.shared.exception.ValidationException;
import jakarta.persistence.*;
import java.util.Objects;

@Entity
@Table(name = "permissions")
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "permission_key", nullable = false, unique = true, length = 100)
    private String key;

    @Column(name = "description", length = 500)
    private String description;

    public static Permission of(String key, String description) {
        Permission p = new Permission();
        p.setKey(key);
        p.setDescription(description);
        return p;
    }

    public boolean matches(String otherKey) {
        return Objects.equals(this.key, otherKey);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getKey() { return key; }
    public void setKey(String key) {
        if (key == null || key.isBlank()) {
            throw new ValidationException("PERM_KEY_REQUIRED", "permission key must not be blank");
        }
        this.key = key;
    }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
