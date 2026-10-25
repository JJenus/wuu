package com.surf.tracker.core.infrastructure.repository;

import com.surf.tracker.core.domain.entity.TripPoint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TripPointRepository extends JpaRepository<TripPoint, Long> {

    @Query("SELECT p FROM TripPoint p WHERE p.trip.tripId = :tripId AND p.trip.vehicle.organizationId = :organizationId ORDER BY p.pointOrder ASC")
    Page<TripPoint> findByTripTripIdOrderByPointOrderAsc(
            @Param("tripId") String tripId, @Param("organizationId") Long organizationId, Pageable pageable);

    @Query("SELECT p FROM TripPoint p WHERE p.tripPointId = :tripPointId AND p.trip.vehicle.organizationId = :organizationId")
    Optional<TripPoint> findByIdAndOrganizationId(
            @Param("tripPointId") Long tripPointId, @Param("organizationId") Long organizationId);

    @Query("SELECT COUNT(p) FROM TripPoint p WHERE p.trip.tripId = :tripId AND p.trip.vehicle.organizationId = :organizationId")
    long countByTripTripIdAndOrganizationId(@Param("tripId") String tripId, @Param("organizationId") Long organizationId);
}
