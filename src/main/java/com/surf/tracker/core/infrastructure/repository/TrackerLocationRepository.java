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