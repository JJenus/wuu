package com.surf.tracker.notification.infrastructure.repository;

import com.surf.tracker.notification.domain.enums.NotificationChannel;
import com.surf.tracker.notification.domain.entity.NotificationTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, Long> {

    Optional<NotificationTemplate> findByTemplateId(String templateId);

    List<NotificationTemplate> findByTemplateTypeAndChannelAndEnabledTrue(String templateType, NotificationChannel channel);

    List<NotificationTemplate> findByChannelAndEnabledTrue(NotificationChannel channel);

    List<NotificationTemplate> findByTemplateTypeAndEnabledTrue(String templateType);

    @Query("SELECT t FROM NotificationTemplate t WHERE " +
           "(:templateType IS NULL OR t.templateType = :templateType) AND " +
           "(:channel IS NULL OR t.channel = :channel) AND " +
           "(:category IS NULL OR t.category = :category) AND " +
           "t.enabled = true " +
           "ORDER BY t.createdAt DESC")
    Page<NotificationTemplate> findWithFilters(
        @Param("templateType") String templateType,
        @Param("channel") NotificationChannel channel,
        @Param("category") String category,
        Pageable pageable
    );

    boolean existsByTemplateId(String templateId);

    void deleteByTemplateId(String templateId);
}
