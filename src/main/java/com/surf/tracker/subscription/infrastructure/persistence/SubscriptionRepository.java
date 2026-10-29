package com.surf.tracker.subscription.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubscriptionRepository extends JpaRepository<SubscriptionEntity, UUID> {

    Optional<SubscriptionEntity> findByOrganizationIdAndUserIdAndId(Long organizationId, Long userId, UUID id);

    Optional<SubscriptionEntity> findByOrganizationIdAndUserIdAndOperationKey(
        Long organizationId, Long userId, String operationKey);

    List<SubscriptionEntity> findAllByOrganizationIdAndUserIdOrderByCreatedAtAsc(
        Long organizationId, Long userId);
}
