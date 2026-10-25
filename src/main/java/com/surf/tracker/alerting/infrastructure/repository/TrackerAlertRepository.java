package com.surf.tracker.alerting.infrastructure.repository;

import com.surf.tracker.alerting.domain.entity.TrackerAlert;
import com.surf.tracker.alerting.domain.enums.AlertSeverity;
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
public interface TrackerAlertRepository extends JpaRepository<TrackerAlert, Long> {

    @Query("SELECT a FROM TrackerAlert a WHERE a.alertId = :alertId AND a.organizationId = :organizationId")
    Optional<TrackerAlert> findByIdAndOrganizationId(@Param("alertId") Long alertId, @Param("organizationId") Long organizationId);

    Page<TrackerAlert> findByVehicleIdAndOrganizationId(String vehicleId, Long organizationId, Pageable pageable);

    Page<TrackerAlert> findByTrackerIdAndOrganizationId(String trackerId, Long organizationId, Pageable pageable);

    List<TrackerAlert> findByAlertTypeAndOrganizationIdAndTriggeredAtAfter(
        String alertType, Long organizationId, Instant triggeredAfter);

    List<TrackerAlert> findBySeverityAndAcknowledgedAndOrganizationId(
            AlertSeverity severity, boolean acknowledged, Long organizationId);

    @Query("SELECT ta FROM TrackerAlert ta WHERE ta.vehicleId = :vehicleId " +
           "AND ta.organizationId = :organizationId " +
           "AND ta.triggeredAt >= :startTime AND ta.triggeredAt <= :endTime " +
           "ORDER BY ta.triggeredAt DESC")
    List<TrackerAlert> findVehicleAlertsInRange(
        @Param("vehicleId") String vehicleId,
        @Param("organizationId") Long organizationId,
        @Param("startTime") Instant startTime,
        @Param("endTime") Instant endTime);

    @Query("SELECT ta FROM TrackerAlert ta WHERE ta.acknowledged = false " +
           "AND ta.resolved = false " +
           "AND ta.organizationId = :organizationId " +
           "ORDER BY ta.severity DESC, ta.triggeredAt DESC")
    Page<TrackerAlert> findActiveAlerts(@Param("organizationId") Long organizationId, Pageable pageable);

    @Query("SELECT ta FROM TrackerAlert ta WHERE ta.vehicleId = :vehicleId " +
           "AND ta.acknowledged = false " +
           "AND ta.resolved = false " +
           "AND ta.organizationId = :organizationId " +
           "ORDER BY ta.triggeredAt DESC")
    List<TrackerAlert> findActiveVehicleAlerts(@Param("vehicleId") String vehicleId, @Param("organizationId") Long organizationId);

    @Query("SELECT COUNT(ta) FROM TrackerAlert ta WHERE ta.acknowledged = false " +
           "AND ta.severity = :severity " +
           "AND ta.organizationId = :organizationId")
    Long countUnacknowledgedBySeverity(@Param("severity") AlertSeverity severity, @Param("organizationId") Long organizationId);

    @Query("SELECT ta.alertType, COUNT(ta) FROM TrackerAlert ta " +
           "WHERE ta.organizationId = :organizationId " +
           "AND ta.triggeredAt >= :startTime " +
           "GROUP BY ta.alertType " +
           "ORDER BY COUNT(ta) DESC")
    List<Object[]> getAlertTypeStatistics(@Param("organizationId") Long organizationId, @Param("startTime") Instant startTime);

    @Query("SELECT ta FROM TrackerAlert ta WHERE ta.resolved = false " +
           "AND ta.triggeredAt < :cutoffTime " +
           "AND ta.organizationId = :organizationId")
    List<TrackerAlert> findStaleAlerts(@Param("cutoffTime") Instant cutoffTime, @Param("organizationId") Long organizationId);

    @Query("SELECT ta FROM TrackerAlert ta WHERE ta.organizationId = :organizationId AND " +
           "(:vehicleId IS NULL OR ta.vehicleId = :vehicleId) " +
           "AND (:trackerId IS NULL OR ta.trackerId = :trackerId) " +
           "AND (:alertType IS NULL OR ta.alertType = :alertType) " +
           "AND (:severity IS NULL OR ta.severity = :severity) " +
           "AND (:acknowledged IS NULL OR ta.acknowledged = :acknowledged) " +
           "AND (:resolved IS NULL OR ta.resolved = :resolved) " +
           "AND (:startDate IS NULL OR ta.triggeredAt >= :startDate) " +
           "AND (:endDate IS NULL OR ta.triggeredAt <= :endDate) " +
           "AND (:search IS NULL OR LOWER(ta.message) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<TrackerAlert> searchAlerts(
            @Param("organizationId") Long organizationId,
            @Param("vehicleId") String vehicleId,
            @Param("trackerId") String trackerId,
            @Param("alertType") String alertType,
            @Param("severity") AlertSeverity severity,
            @Param("acknowledged") Boolean acknowledged,
            @Param("resolved") Boolean resolved,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate,
            @Param("search") String search,
            Pageable pageable);

    @Query("SELECT ta FROM TrackerAlert ta WHERE ta.alertId IN :alertIds AND ta.organizationId = :organizationId")
    List<TrackerAlert> findByIds(@Param("alertIds") List<Long> alertIds, @Param("organizationId") Long organizationId);

    @Query("SELECT COUNT(ta) FROM TrackerAlert ta WHERE ta.organizationId = :organizationId " +
           "AND ta.triggeredAt >= :startTime AND ta.triggeredAt <= :endTime " +
           "AND ta.severity = :severity")
    Long countBySeverityAndTimeRange(
            @Param("organizationId") Long organizationId,
            @Param("severity") AlertSeverity severity,
            @Param("startTime") Instant startTime,
            @Param("endTime") Instant endTime);

    @Query("SELECT ta.vehicleId, COUNT(ta) FROM TrackerAlert ta " +
           "WHERE ta.organizationId = :organizationId " +
           "AND ta.triggeredAt >= :startTime " +
           "GROUP BY ta.vehicleId " +
           "ORDER BY COUNT(ta) DESC")
    List<Object[]> getAlertCountByVehicle(@Param("organizationId") Long organizationId, @Param("startTime") Instant startTime);

    @Query("SELECT AVG(TIMESTAMPDIFF(SECOND, ta.triggeredAt, ta.resolvedAt)) " +
           "FROM TrackerAlert ta WHERE ta.resolved = true " +
           "AND ta.resolvedAt IS NOT NULL " +
           "AND ta.triggeredAt >= :startTime " +
           "AND ta.organizationId = :organizationId")
    Double getAverageResolutionTime(@Param("startTime") Instant startTime, @Param("organizationId") Long organizationId);
}
