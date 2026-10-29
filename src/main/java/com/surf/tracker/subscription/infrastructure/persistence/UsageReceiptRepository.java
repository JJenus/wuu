package com.surf.tracker.subscription.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface UsageReceiptRepository extends JpaRepository<UsageReceiptEntity, UUID> {
    Optional<UsageReceiptEntity> findBySubscriptionIdAndOperationKey(UUID subscriptionId, String operationKey);
}
