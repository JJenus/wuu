package com.surf.tracker.core.infrastructure.repository;

import com.surf.tracker.core.domain.entity.TrackerRawData;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Repository
public interface TrackerRawDataRepository extends JpaRepository<TrackerRawData, Long> {
    
    Page<TrackerRawData> findByTrackerTrackerId(String trackerId, Pageable pageable);
    
    List<TrackerRawData> findByProcessed(boolean processed);
    
    List<TrackerRawData> findByReceivedAtBetween(Instant startTime, Instant endTime);
    
    @Query("SELECT COUNT(trd) FROM TrackerRawData trd WHERE trd.processed = false")
    Long countUnprocessed();
    
    @Transactional
    @Modifying
    @Query("UPDATE TrackerRawData trd SET trd.processed = true, trd.parsedData = :parsedData " +
           "WHERE trd.rawDataId = :rawDataId")
    int markAsProcessed(@Param("rawDataId") Long rawDataId, @Param("parsedData") String parsedData);
    
    @Transactional
    @Modifying
    @Query("UPDATE TrackerRawData trd SET trd.processed = false, trd.processingError = :error " +
           "WHERE trd.rawDataId = :rawDataId")
    int markAsFailed(@Param("rawDataId") Long rawDataId, @Param("error") String error);
    
    @Transactional
    @Modifying
    @Query("DELETE FROM TrackerRawData trd WHERE trd.receivedAt < :cutoffTime")
    int deleteOldRecords(@Param("cutoffTime") Instant cutoffTime);
}