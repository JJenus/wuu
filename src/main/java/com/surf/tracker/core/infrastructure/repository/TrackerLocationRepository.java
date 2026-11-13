package com.surf.tracker.core.infrastructure.repository;

import com.surf.tracker.core.domain.entity.TrackerLocation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface TrackerLocationRepository extends JpaRepository<TrackerLocation, Long> {

    @Query("SELECT tl FROM TrackerLocation tl WHERE tl.trip.tripId = :tripId " +
            "AND tl.trip.vehicle.organizationId = :organizationId " +
            "AND tl.tracker.vehicle.vehicleId = tl.trip.vehicle.vehicleId " +
            "AND tl.tracker.vehicle.organizationId = :organizationId " +
            "AND tl.validity = 'A' AND tl.latitude BETWEEN -90 AND 90 " +
            "AND tl.longitude BETWEEN -180 AND 180 ORDER BY tl.recordedAt, tl.locationId")
    List<TrackerLocation> findAssociatedTrip(@Param("tripId") String tripId,
            @Param("organizationId") Long organizationId, Pageable pageable);

    @Query("SELECT tl FROM TrackerLocation tl WHERE tl.tracker.vehicle.vehicleId = :vehicleId " +
            "AND tl.tracker.vehicle.organizationId = :organizationId " +
            "AND tl.recordedAt >= :start AND tl.recordedAt <= :end " +
            "AND tl.validity = 'A' AND tl.latitude BETWEEN -90 AND 90 " +
            "AND tl.longitude BETWEEN -180 AND 180 ORDER BY tl.recordedAt, tl.locationId")
    List<TrackerLocation> findRecordedWindow(@Param("vehicleId") String vehicleId,
            @Param("organizationId") Long organizationId, @Param("start") Instant start,
            @Param("end") Instant end, Pageable pageable);
    
    List<TrackerLocation> findByTrackerTrackerIdOrderByRecordedAtDesc(String trackerId);
    
    Page<TrackerLocation> findByTrackerTrackerId(String trackerId, Pageable pageable);
    
    @Query("SELECT tl FROM TrackerLocation tl WHERE tl.tracker.trackerId = :trackerId " +
           "AND tl.recordedAt BETWEEN :startTime AND :endTime " +
           "ORDER BY tl.recordedAt")
    List<TrackerLocation> findByTrackerAndTimeRange(
        @Param("trackerId") String trackerId,
        @Param("startTime") Instant startTime,
        @Param("endTime") Instant endTime);
    
    @Query("SELECT tl FROM TrackerLocation tl WHERE tl.tracker.deviceId = :deviceId " +
           "ORDER BY tl.recordedAt DESC LIMIT 1")
    TrackerLocation findLatestByDeviceId(@Param("deviceId") String deviceId);
    
    @Query("SELECT COUNT(tl) FROM TrackerLocation tl WHERE tl.tracker.trackerId = :trackerId")
    Long countByTrackerId(@Param("trackerId") String trackerId);
    
    @Query("SELECT tl FROM TrackerLocation tl WHERE tl.accStatus = false " +
           "AND tl.recordedAt >= :startTime")
    List<TrackerLocation> findAccOffEvents(@Param("startTime") Instant startTime);
}
