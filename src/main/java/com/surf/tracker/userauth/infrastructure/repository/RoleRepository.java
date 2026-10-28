package com.surf.tracker.userauth.infrastructure.repository;

import com.surf.tracker.userauth.domain.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {
    Optional<Role> findByNameAndOrgIsNull(String name);
    Optional<Role> findByNameAndOrgId(String name, Long orgId);
    List<Role> findByOrgId(Long orgId);
    List<Role> findByOrgIsNull();
}
