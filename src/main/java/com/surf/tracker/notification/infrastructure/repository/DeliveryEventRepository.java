package com.surf.tracker.notification.infrastructure.repository;

import com.surf.tracker.notification.domain.entity.DeliveryEvent;
import com.surf.tracker.notification.domain.enums.DeliveryEventType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DeliveryEventRepository extends JpaRepository<DeliveryEvent, Long> {

    List<DeliveryEvent> findByDeliveryId(String deliveryId);

    List<DeliveryEvent> findByDeliveryIdOrderByOccurredAt(String deliveryId);

    Page<DeliveryEvent> findByDeliveryIdOrderByOccurredAtDesc(String deliveryId, Pageable pageable);

    List<DeliveryEvent> findByDeliveryIdAndEventType(String deliveryId, DeliveryEventType eventType);

    long countByDeliveryId(String deliveryId);
}
