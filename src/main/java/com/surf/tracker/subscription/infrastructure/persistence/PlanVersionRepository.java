package com.surf.tracker.subscription.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlanVersionRepository extends JpaRepository<PlanVersionEntity, UUID> {

    Optional<PlanVersionEntity> findByPlanIdAndVersion(UUID planId, long version);

    boolean existsByPlanIdAndVersion(UUID planId, long version);

    List<PlanVersionEntity> findAllByOrderByPlanIdAscVersionAsc();
}
