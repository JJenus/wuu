package com.surf.tracker.alerting.infrastructure.repository;

import com.surf.tracker.alerting.domain.entity.GeofencePoint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GeofencePointRepository extends JpaRepository<GeofencePoint, Long> {

    @Query("SELECT p FROM GeofencePoint p WHERE p.geofence.geofenceId = :geofenceId AND p.geofence.organizationId = :organizationId ORDER BY p.pointOrder ASC")
    List<GeofencePoint> findByGeofenceGeofenceIdOrderByPointOrderAsc(
            @Param("geofenceId") Long geofenceId, @Param("organizationId") Long organizationId);

    @Query("SELECT p FROM GeofencePoint p WHERE p.geofence.geofenceId = :geofenceId AND p.geofence.organizationId = :organizationId ORDER BY p.pointOrder ASC")
    Page<GeofencePoint> findByGeofenceGeofenceIdOrderByPointOrderAsc(
            @Param("geofenceId") Long geofenceId, @Param("organizationId") Long organizationId, Pageable pageable);

    @Query("SELECT p FROM GeofencePoint p WHERE p.pointId = :pointId AND p.geofence.organizationId = :organizationId")
    Optional<GeofencePoint> findByIdAndOrganizationId(@Param("pointId") Long pointId, @Param("organizationId") Long organizationId);
}
