package com.surf.tracker.core.infrastructure.repository;

import com.surf.tracker.core.domain.entity.Tracker;
import com.surf.tracker.core.domain.enums.TrackerStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface TrackerRepository extends JpaRepository<Tracker, String> {
    
    @Query("SELECT t FROM Tracker t WHERE t.deviceId = :deviceId AND t.vehicle.organizationId = :organizationId")
    Optional<Tracker> findByDeviceIdAndOrganizationId(@Param("deviceId") String deviceId, @Param("organizationId") Long organizationId);

    @Query("SELECT t FROM Tracker t WHERE t.trackerId = :trackerId AND t.vehicle.organizationId = :organizationId")
    Optional<Tracker> findByIdAndOrganizationId(@Param("trackerId") String trackerId, @Param("organizationId") Long organizationId);

    @Query("SELECT t FROM Tracker t WHERE t.vehicle.vehicleId = :vehicleId AND t.vehicle.organizationId = :organizationId")
    Optional<Tracker> findByVehicleId(@Param("vehicleId") String vehicleId, @Param("organizationId") Long organizationId);
    
    @Query("SELECT t FROM Tracker t WHERE t.isOnline = :isOnline AND t.vehicle.organizationId = :organizationId")
    Page<Tracker> findByIsOnlineAndOrganizationId(@Param("isOnline") boolean isOnline, @Param("organizationId") Long organizationId, Pageable pageable);
    
    @Query("SELECT t FROM Tracker t WHERE t.status = :status AND t.vehicle.organizationId = :organizationId")
    Page<Tracker> findByStatusAndOrganizationId(@Param("status") TrackerStatus status, @Param("organizationId") Long organizationId, Pageable pageable);
    
    @Query("SELECT t FROM Tracker t WHERE t.lastSeen < :cutoffTime AND t.isOnline = true AND t.vehicle.organizationId = :organizationId")
    Page<Tracker> findStaleConnections(@Param("cutoffTime") Instant cutoffTime, @Param("organizationId") Long organizationId, Pageable pageable);
    
    @Query("SELECT t FROM Tracker t WHERE t.batteryLevel < :threshold AND t.vehicle.organizationId = :organizationId")
    Page<Tracker> findTrackersWithLowBattery(@Param("threshold") float threshold, @Param("organizationId") Long organizationId, Pageable pageable);
    
    @Query("SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END FROM Tracker t WHERE t.deviceId = :deviceId AND t.vehicle.organizationId = :organizationId")
    boolean existsByDeviceIdAndOrganizationId(@Param("deviceId") String deviceId, @Param("organizationId") Long organizationId);

    @Query("SELECT t FROM Tracker t WHERE t.vehicle.organizationId = :organizationId")
    Page<Tracker> findByOrganizationId(@Param("organizationId") Long organizationId, Pageable pageable);

    @Modifying
    @Query("DELETE FROM Tracker t WHERE t.trackerId = :trackerId AND t.vehicle.organizationId = :organizationId")
    long deleteByIdAndOrganizationId(@Param("trackerId") String trackerId, @Param("organizationId") Long organizationId);
}
