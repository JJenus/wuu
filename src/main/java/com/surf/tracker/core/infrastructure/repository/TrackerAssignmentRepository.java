package com.surf.tracker.core.infrastructure.repository;

import com.surf.tracker.core.domain.entity.TrackerAssignment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TrackerAssignmentRepository extends JpaRepository<TrackerAssignment, Long> {

    @Query("SELECT a FROM TrackerAssignment a WHERE a.vehicle.organizationId = :organizationId")
    Page<TrackerAssignment> findByOrganizationId(@Param("organizationId") Long organizationId, Pageable pageable);

    @Query("SELECT a FROM TrackerAssignment a WHERE a.id = :id AND a.vehicle.organizationId = :organizationId")
    Optional<TrackerAssignment> findByIdAndOrganizationId(@Param("id") Long id, @Param("organizationId") Long organizationId);

    @Query("SELECT a FROM TrackerAssignment a WHERE a.vehicle.organizationId = :organizationId AND a.isActive = :isActive")
    Page<TrackerAssignment> findByIsActiveAndOrganizationId(
            @Param("isActive") Boolean isActive, @Param("organizationId") Long organizationId, Pageable pageable);

    @Query("SELECT a FROM TrackerAssignment a WHERE a.vehicle.vehicleId = :vehicleId AND a.vehicle.organizationId = :organizationId")
    Page<TrackerAssignment> findByVehicleVehicleIdAndOrganizationId(
            @Param("vehicleId") String vehicleId, @Param("organizationId") Long organizationId, Pageable pageable);

    @Query("SELECT a FROM TrackerAssignment a WHERE a.tracker.trackerId = :trackerId AND a.vehicle.organizationId = :organizationId")
    Page<TrackerAssignment> findByTrackerTrackerIdAndOrganizationId(
            @Param("trackerId") String trackerId, @Param("organizationId") Long organizationId, Pageable pageable);

    @Query("SELECT a FROM TrackerAssignment a WHERE a.vehicle.vehicleId = :vehicleId AND a.isActive = :isActive AND a.vehicle.organizationId = :organizationId ORDER BY a.assignedAt DESC")
    Optional<TrackerAssignment> findActiveAssignmentForVehicle(
            @Param("vehicleId") String vehicleId, @Param("isActive") Boolean isActive,
            @Param("organizationId") Long organizationId);

    @Modifying
    @Query("DELETE FROM TrackerAssignment a WHERE a.id = :id AND a.vehicle.organizationId = :organizationId")
    long deleteByIdAndOrganizationId(@Param("id") Long id, @Param("organizationId") Long organizationId);
}
