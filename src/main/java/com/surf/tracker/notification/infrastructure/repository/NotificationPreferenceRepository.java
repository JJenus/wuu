package com.surf.tracker.notification.infrastructure.repository;

import com.surf.tracker.notification.domain.entity.NotificationPreference;
import com.surf.tracker.notification.domain.enums.NotificationChannel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, Long> {

    List<NotificationPreference> findByUserIdAndTenantId(String userId, String tenantId);

    void deleteByUserIdAndTenantId(String userId, String tenantId);

    List<NotificationPreference> findByUserId(String userId);

    List<NotificationPreference> findByCategory(String category);

    Optional<NotificationPreference> findByUserIdAndCategory(String userId, String category);

    List<NotificationPreference> findByUserIdAndCategoryIn(String userId, List<String> categories);

    void deleteByUserId(String userId);

    void deleteByUserIdAndCategory(String userId, String category);
}
