package com.surf.tracker.notification.infrastructure.repository;

import com.surf.tracker.notification.domain.entity.Delivery;
import com.surf.tracker.notification.domain.enums.DeliveryStatus;
import com.surf.tracker.notification.domain.enums.NotificationChannel;
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
public interface DeliveryRepository extends JpaRepository<Delivery, Long> {

    Optional<Delivery> findByDeliveryIdAndNotificationHubUserIdAndNotificationHubTenantId(
        String deliveryId, String userId, String tenantId);

    @Query("SELECT d FROM Delivery d WHERE d.notificationHub.userId = :userId AND d.notificationHub.tenantId = :tenantId "
        + "AND (:status IS NULL OR d.status = :status) AND (:channel IS NULL OR d.channel = :channel) "
        + "AND (:alertId IS NULL OR d.notificationHub.alertId = :alertId)")
    Page<Delivery> findForRecipient(@Param("userId") String userId, @Param("tenantId") String tenantId,
        @Param("status") DeliveryStatus status, @Param("channel") NotificationChannel channel,
        @Param("alertId") String alertId, Pageable pageable);

    Optional<Delivery> findByDeliveryId(String deliveryId);

    List<Delivery> findByNotificationHubNotificationId(String notificationId);

    Page<Delivery> findByNotificationHubUserId(String userId, Pageable pageable);

    List<Delivery> findByStatus(DeliveryStatus status);

    @Query("SELECT d FROM Delivery d WHERE d.status = :status AND d.nextRetryAt <= :now AND d.attemptCount < d.maxAttempts")
    List<Delivery> findByStatusAndNextRetryAtLessThanEqual(
        @Param("status") DeliveryStatus status,
        @Param("now") Instant now
    );

    @Query("SELECT d FROM Delivery d WHERE d.notificationHub.userId = :userId AND d.status = :status")
    Page<Delivery> findByUserIdAndStatus(
        @Param("userId") String userId,
        @Param("status") DeliveryStatus status,
        Pageable pageable
    );

    @Query("SELECT d FROM Delivery d WHERE d.channel = :channel AND d.status = :status")
    Page<Delivery> findByChannelAndStatus(
        @Param("channel") NotificationChannel channel,
        @Param("status") DeliveryStatus status,
        Pageable pageable
    );

    long countByNotificationHubNotificationIdAndStatus(String notificationId, DeliveryStatus status);
}
