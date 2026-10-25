package com.surf.tracker.userauth.infrastructure.repository;

import com.surf.tracker.userauth.domain.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"roles", "roles.org", "roles.permissions"})
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findWithAuthoritiesById(@Param("id") Long id);

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByEmailIgnoreCase(String email);

    Page<User> findByEnabledTrueAndLockedFalse(Pageable pageable);

    @Query("SELECT COUNT(DISTINCT u) FROM User u JOIN u.roles r WHERE r.org.id = :orgId AND r.name = 'TENANT_ADMIN' AND u.enabled = true")
    long countAdminsInOrg(@Param("orgId") Long orgId);

    @Query("SELECT u FROM User u JOIN u.roles r WHERE r.org.id = :orgId")
    Page<User> findByOrgId(@Param("orgId") Long orgId, Pageable pageable);

    @Query("SELECT DISTINCT u FROM User u JOIN u.roles r WHERE u.id = :userId AND r.org.id = :orgId")
    Optional<User> findByIdAndOrgId(@Param("userId") Long userId, @Param("orgId") Long orgId);
}
