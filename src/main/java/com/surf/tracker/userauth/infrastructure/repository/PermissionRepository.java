package com.surf.tracker.userauth.infrastructure.repository;

import com.surf.tracker.userauth.domain.entity.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PermissionRepository extends JpaRepository<Permission, Long> {
    Optional<Permission> findByKey(String key);
    List<Permission> findByKeyIn(Collection<String> keys);
}
