package com.surf.tracker.userauth.infrastructure.repository;

import com.surf.tracker.userauth.domain.entity.Organization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OrganizationRepository extends JpaRepository<Organization, Long> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT o FROM Organization o WHERE o.id = :id")
    Optional<Organization> lockById(@org.springframework.data.repository.query.Param("id") Long id);
    Optional<Organization> findBySlug(String slug);
    boolean existsBySlug(String slug);
}
