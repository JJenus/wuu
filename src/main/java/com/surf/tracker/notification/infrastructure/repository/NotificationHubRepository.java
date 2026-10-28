package com.surf.tracker.notification.infrastructure.repository;

import com.surf.tracker.notification.domain.entity.NotificationHub;
import com.surf.tracker.notification.domain.enums.NotificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationHubRepository extends JpaRepository<NotificationHub, Long> {

    Optional<NotificationHub> findByNotificationId(String notificationId);

    Page<NotificationHub> findByUserId(String userId, Pageable pageable);

    Page<NotificationHub> findByTenantId(String tenantId, Pageable pageable);

    List<NotificationHub> findByAlertId(String alertId);

    Page<NotificationHub> findByUserIdAndStatus(String userId, NotificationStatus status, Pageable pageable);

    @Query("SELECT h FROM NotificationHub h WHERE " +
           "(:userId IS NULL OR h.userId = :userId) AND " +
           "(:status IS NULL OR h.status = :status) AND " +
           "(:alertId IS NULL OR h.alertId = :alertId) " +
           "ORDER BY h.createdAt DESC")
    Page<NotificationHub> findWithFilters(
        @Param("userId") String userId,
        @Param("status") NotificationStatus status,
        @Param("alertId") String alertId,
        Pageable pageable
    );
}
