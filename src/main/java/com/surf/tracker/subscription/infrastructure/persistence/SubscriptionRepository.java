package com.surf.tracker.subscription.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubscriptionRepository extends JpaRepository<SubscriptionEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SubscriptionEntity s where s.organizationId=:org and s.id=:id")
    Optional<SubscriptionEntity> lockForAdministrator(@Param("org") Long organizationId, @Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SubscriptionEntity s where s.organizationId=:org and s.userId=:user and s.id=:id")
    Optional<SubscriptionEntity> lockForOwner(@Param("org") Long organizationId, @Param("user") Long userId,
        @Param("id") UUID id);

    Optional<SubscriptionEntity> findByOrganizationIdAndUserIdAndId(Long organizationId, Long userId, UUID id);

    Optional<SubscriptionEntity> findByOrganizationIdAndUserIdAndOperationKey(
        Long organizationId, Long userId, String operationKey);

    List<SubscriptionEntity> findAllByOrganizationIdAndUserIdOrderByCreatedAtAsc(
        Long organizationId, Long userId);
}
