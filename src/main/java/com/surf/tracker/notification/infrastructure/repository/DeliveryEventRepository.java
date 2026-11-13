package com.surf.tracker.notification.infrastructure.repository;

import com.surf.tracker.notification.domain.entity.DeliveryEvent;
import com.surf.tracker.notification.domain.enums.DeliveryEventType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;

import java.util.List;

@Repository
public interface DeliveryEventRepository extends JpaRepository<DeliveryEvent, Long> {

    /** One atomic statement; no application-side inbox list or truncated first page. */
    @Modifying(flushAutomatically = true)
    @Query(value = """
        INSERT INTO delivery_events (deliveryId, eventType, metadata, occurredAt)
        SELECT d.deliveryId, 'OPENED', 'Bulk read', :now
        FROM deliveries d JOIN notification_hubs h ON h.notificationId = d.notification_id
        WHERE h.userId = :userId AND h.tenantId = :tenantId
          AND NOT EXISTS (SELECT 1 FROM delivery_events e
                          WHERE e.deliveryId = d.deliveryId AND e.eventType = 'OPENED')
        """, nativeQuery = true)
    int markUnreadForRecipient(@Param("userId") String userId, @Param("tenantId") String tenantId,
        @Param("now") Instant now);

    List<DeliveryEvent> findByDeliveryId(String deliveryId);

    List<DeliveryEvent> findByDeliveryIdOrderByOccurredAt(String deliveryId);

    Page<DeliveryEvent> findByDeliveryIdOrderByOccurredAtDesc(String deliveryId, Pageable pageable);

    List<DeliveryEvent> findByDeliveryIdAndEventType(String deliveryId, DeliveryEventType eventType);

    long countByDeliveryId(String deliveryId);
}
