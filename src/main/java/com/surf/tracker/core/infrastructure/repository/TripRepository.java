package com.surf.tracker.core.infrastructure.repository;

import com.surf.tracker.core.domain.entity.Trip;
import com.surf.tracker.core.domain.enums.TripEndReason;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface TripRepository extends JpaRepository<Trip, String> {
    
    @Query("SELECT t FROM Trip t WHERE t.vehicle.vehicleId = :vehicleId AND t.vehicle.organizationId = :organizationId")
    Page<Trip> findByVehicleVehicleIdAndOrganizationId(
        @Param("vehicleId") String vehicleId, @Param("organizationId") Long organizationId, Pageable pageable);
    
    @Query("SELECT t FROM Trip t WHERE t.vehicle.vehicleId = :vehicleId AND t.isActive = :isActive AND t.vehicle.organizationId = :organizationId")
    Optional<Trip> findByVehicleVehicleIdAndIsActiveAndOrganizationId(
        @Param("vehicleId") String vehicleId, @Param("isActive") boolean isActive, @Param("organizationId") Long organizationId);
    
    @Query("SELECT t FROM Trip t WHERE t.vehicle.vehicleId = :vehicleId AND t.startTime BETWEEN :startTime AND :endTime AND t.vehicle.organizationId = :organizationId")
    Page<Trip> findByVehicleVehicleIdAndStartTimeBetweenAndOrganizationId(
        @Param("vehicleId") String vehicleId, @Param("startTime") Instant startTime, @Param("endTime") Instant endTime,
        @Param("organizationId") Long organizationId, Pageable pageable);
    
    @Query("SELECT t FROM Trip t WHERE t.isActive = :isActive AND t.vehicle.organizationId = :organizationId")
    Page<Trip> findByIsActiveAndOrganizationId(@Param("isActive") boolean isActive, @Param("organizationId") Long organizationId, Pageable pageable);
    
    @Query("SELECT t FROM Trip t WHERE t.vehicle.vehicleId = :vehicleId " +
           "AND t.endReason = :endReason " +
           "AND t.endTime >= :startTime " +
           "AND t.vehicle.organizationId = :organizationId")
    List<Trip> findTripsByEndReason(
        @Param("vehicleId") String vehicleId,
        @Param("endReason") TripEndReason endReason,
        @Param("startTime") Instant startTime,
        @Param("organizationId") Long organizationId);
    
    @Query("SELECT t FROM Trip t WHERE t.vehicle.vehicleId = :vehicleId " +
           "AND t.isActive = false " +
           "AND t.totalDistanceKm > :minDistance " +
           "AND t.vehicle.organizationId = :organizationId " +
           "ORDER BY t.endTime DESC")
    Page<Trip> findRecentCompletedTrips(
        @Param("vehicleId") String vehicleId,
        @Param("minDistance") float minDistance,
        @Param("organizationId") Long organizationId,
        Pageable pageable);
    
    @Query("SELECT SUM(t.totalDistanceKm) FROM Trip t " +
           "WHERE t.vehicle.vehicleId = :vehicleId " +
           "AND t.isActive = false " +
           "AND t.endTime BETWEEN :startTime AND :endTime " +
           "AND t.vehicle.organizationId = :organizationId")
    Float getTotalDistanceForPeriod(
        @Param("vehicleId") String vehicleId,
        @Param("startTime") Instant startTime,
        @Param("endTime") Instant endTime,
        @Param("organizationId") Long organizationId);

    @Query("SELECT t FROM Trip t WHERE t.vehicle.organizationId = :organizationId")
    Page<Trip> findByOrganizationId(@Param("organizationId") Long organizationId, Pageable pageable);

    @Query("SELECT t FROM Trip t WHERE t.tripId = :tripId AND t.vehicle.organizationId = :organizationId")
    Optional<Trip> findByIdAndOrganizationId(@Param("tripId") String tripId, @Param("organizationId") Long organizationId);
}
