package com.surf.tracker.core.infrastructure.repository;

import com.surf.tracker.core.domain.entity.TripPoint;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface TripPointRepository extends JpaRepository<TripPoint, Long> {
    Optional<TripPoint> findFirstByTripTripIdOrderByPointOrderDesc(String tripId);
}
