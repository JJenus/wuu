package com.surf.tracker.alerting.infrastructure.repository;

import com.surf.tracker.alerting.domain.entity.TrackerAlert;
import com.surf.tracker.alerting.domain.enums.AlertSeverity;
import com.surf.tracker.alerting.domain.enums.AlertType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface TrackerAlertRepository extends JpaRepository<TrackerAlert, Long>, JpaSpecificationExecutor<TrackerAlert> {

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

    default Page<TrackerAlert> searchAlerts(
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
            Pageable pageable) {
        // Omit absent filters instead of binding untyped NULLs in vendor SQL.
        return findAll((root, query, builder) -> {
            if (organizationId == null || organizationId <= 0) return builder.disjunction();
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            predicates.add(builder.equal(root.get("organizationId"), organizationId));
            if (vehicleId != null) predicates.add(builder.equal(root.get("vehicleId"), vehicleId));
            if (trackerId != null) predicates.add(builder.equal(root.get("trackerId"), trackerId));
            if (alertType != null) predicates.add(builder.equal(root.get("alertType"), AlertType.valueOf(alertType)));
            if (severity != null) predicates.add(builder.equal(root.get("severity"), severity));
            if (acknowledged != null) predicates.add(builder.equal(root.get("acknowledged"), acknowledged));
            if (resolved != null) predicates.add(builder.equal(root.get("resolved"), resolved));
            if (startDate != null) predicates.add(builder.greaterThanOrEqualTo(root.get("triggeredAt"), startDate));
            if (endDate != null) predicates.add(builder.lessThanOrEqualTo(root.get("triggeredAt"), endDate));
            if (search != null) predicates.add(builder.like(builder.lower(root.get("message")),
                "%" + search.toLowerCase(java.util.Locale.ROOT) + "%"));
            return builder.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        }, pageable);
    }

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
