package com.surf.tracker.notification.application;

import com.surf.tracker.notification.api.dto.*;
import com.surf.tracker.notification.domain.entity.Delivery;
import com.surf.tracker.notification.domain.entity.NotificationPreference;
import com.surf.tracker.notification.domain.entity.NotificationTemplate;
import com.surf.tracker.notification.domain.enums.DeliveryStatus;
import com.surf.tracker.notification.domain.enums.NotificationChannel;
import com.surf.tracker.notification.domain.enums.NotificationStatus;
import com.surf.tracker.notification.infrastructure.repository.DeliveryRepository;
import com.surf.tracker.notification.infrastructure.repository.NotificationPreferenceRepository;
import com.surf.tracker.notification.infrastructure.repository.NotificationTemplateRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class NotificationQueryService {

    private final DeliveryRepository deliveryRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final NotificationTemplateRepository templateRepository;

    public NotificationQueryService(
        DeliveryRepository deliveryRepository,
        NotificationPreferenceRepository preferenceRepository,
        NotificationTemplateRepository templateRepository
    ) {
        this.deliveryRepository = deliveryRepository;
        this.preferenceRepository = preferenceRepository;
        this.templateRepository = templateRepository;
    }

    public Page<NotificationResponse> findNotifications(
        String userId,
        String status,
        String channel,
        String alertId,
        Pageable pageable
    ) {
        String recipient = NotificationScope.user(userId);
        DeliveryStatus deliveryStatus = status != null ? DeliveryStatus.valueOf(status) : null;
        Page<Delivery> deliveries = deliveryRepository.findForRecipient(recipient, NotificationScope.tenant(),
            deliveryStatus, channel == null ? null : NotificationChannel.valueOf(channel), alertId, pageable);

        return deliveries.map(this::toResponse);
    }

    public NotificationResponse getNotificationById(String deliveryId) {
        Delivery delivery = deliveryRepository.findByDeliveryIdAndNotificationHubUserIdAndNotificationHubTenantId(
                deliveryId, NotificationScope.user(null), NotificationScope.tenant())
            .orElseThrow(() -> new IllegalArgumentException("Delivery not found: " + deliveryId));
        return toResponse(delivery);
    }

    public Page<NotificationResponse> getUserNotifications(
        String userId,
        boolean unreadOnly,
        Pageable pageable
    ) {
        Page<Delivery> deliveries = deliveryRepository.findForRecipient(NotificationScope.user(userId),
            NotificationScope.tenant(), null, null, null, pageable);
        return deliveries.map(this::toResponse);
    }

    public List<NotificationPreferenceResponse> getUserPreferences(String userId) {
        List<NotificationPreference> preferences = preferenceRepository.findByUserIdAndTenantId(
            NotificationScope.user(userId), NotificationScope.tenant());
        return preferences.stream()
            .map(this::toPreferenceResponse)
            .collect(Collectors.toList());
    }

    public Page<NotificationTemplateResponse> getTemplates(
        String templateType,
        String channel,
        Pageable pageable
    ) {
        NotificationChannel ch = channel != null ? NotificationChannel.valueOf(channel) : null;
        Page<NotificationTemplate> templates = templateRepository.findWithFilters(
            templateType, ch, null, pageable
        );
        return templates.map(this::toTemplateResponse);
    }

    private NotificationResponse toResponse(Delivery delivery) {
        NotificationResponse response = new NotificationResponse();
        response.setDeliveryId(delivery.getDeliveryId());
        response.setAlertId(delivery.getNotificationHub() != null
            ? delivery.getNotificationHub().getAlertId() : null);
        response.setChannel(delivery.getChannel().name());
        response.setRecipient(delivery.getRecipient());
        response.setTitle(delivery.getTitle());
        response.setMessage(delivery.getMessage());
        response.setStatus(delivery.getStatus().name());
        response.setSentAt(delivery.getSentAt());
        response.setDeliveredAt(delivery.getDeliveredAt());
        response.setCreatedAt(delivery.getCreatedAt());
        return response;
    }

    private NotificationPreferenceResponse toPreferenceResponse(NotificationPreference preference) {
        NotificationPreferenceResponse response = new NotificationPreferenceResponse();
        response.setUserId(preference.getUserId());
        response.setCategory(preference.getCategory());
        response.setEnabled(preference.isEnabled());
        response.setEnabledChannels(preference.getEnabledChannels().stream()
            .map(Enum::name)
            .collect(Collectors.toSet()));
        response.setUpdatedAt(preference.getUpdatedAt());
        return response;
    }

    private NotificationTemplateResponse toTemplateResponse(NotificationTemplate template) {
        NotificationTemplateResponse response = new NotificationTemplateResponse();
        response.setTemplateId(template.getTemplateId());
        response.setName(template.getName());
        response.setTemplateType(template.getTemplateType());
        response.setCategory(template.getCategory());
        response.setChannel(template.getChannel().name());
        response.setSubjectTemplate(template.getSubjectTemplate());
        response.setBodyTemplate(template.getBodyTemplate());
        response.setLanguage(template.getLanguage());
        response.setEnabled(template.isEnabled());
        response.setMandatory(template.isMandatory());
        response.setCreatedAt(template.getCreatedAt());
        return response;
    }
}
