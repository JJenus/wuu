package com.surf.tracker.notification.application;

import com.surf.tracker.notification.api.dto.*;
import com.surf.tracker.notification.domain.entity.Delivery;
import com.surf.tracker.notification.domain.entity.DeliveryEvent;
import com.surf.tracker.notification.domain.entity.NotificationPreference;
import com.surf.tracker.notification.domain.entity.NotificationTemplate;
import com.surf.tracker.notification.domain.enums.DeliveryEventType;
import com.surf.tracker.notification.domain.enums.NotificationChannel;
import com.surf.tracker.notification.infrastructure.repository.DeliveryEventRepository;
import com.surf.tracker.notification.infrastructure.repository.DeliveryRepository;
import com.surf.tracker.notification.infrastructure.repository.NotificationPreferenceRepository;
import com.surf.tracker.notification.infrastructure.repository.NotificationTemplateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
@Transactional
public class NotificationCommandService {

    private final DeliveryRepository deliveryRepository;
    private final DeliveryEventRepository eventRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final NotificationTemplateRepository templateRepository;
    private final Clock clock;
    private final Supplier<UUID> uuidSupplier;

    @org.springframework.beans.factory.annotation.Autowired
    public NotificationCommandService(
        DeliveryRepository deliveryRepository,
        DeliveryEventRepository eventRepository,
        NotificationPreferenceRepository preferenceRepository,
        NotificationTemplateRepository templateRepository,
        Clock clock
    ) {
        this(deliveryRepository, eventRepository, preferenceRepository, templateRepository, clock, UUID::randomUUID);
    }

    NotificationCommandService(
        DeliveryRepository deliveryRepository,
        DeliveryEventRepository eventRepository,
        NotificationPreferenceRepository preferenceRepository,
        NotificationTemplateRepository templateRepository,
        Clock clock,
        Supplier<UUID> uuidSupplier
    ) {
        this.deliveryRepository = deliveryRepository;
        this.eventRepository = eventRepository;
        this.preferenceRepository = preferenceRepository;
        this.templateRepository = templateRepository;
        this.clock = clock;
        this.uuidSupplier = uuidSupplier;
    }

    public void markAsRead(String deliveryId) {
        Delivery delivery = deliveryRepository.findByDeliveryIdAndNotificationHubUserIdAndNotificationHubTenantId(
                deliveryId, NotificationScope.user(null), NotificationScope.tenant())
            .orElseThrow(() -> new IllegalArgumentException("Delivery not found: " + deliveryId));

        DeliveryEvent event = DeliveryEvent.opened(deliveryId, "Marked as read", clock.instant());
        eventRepository.save(event);
    }

    public void markAllAsRead(String userId) {
        List<Delivery> deliveries = deliveryRepository.findForRecipient(
            NotificationScope.user(userId), NotificationScope.tenant(), null, null, null,
            org.springframework.data.domain.PageRequest.of(0, 1000)
        ).getContent();

        Instant now = clock.instant();
        for (Delivery delivery : deliveries) {
            eventRepository.save(DeliveryEvent.opened(delivery.getDeliveryId(), "Bulk read", now));
        }
    }

    public void deleteDelivery(String deliveryId) {
        Delivery delivery = deliveryRepository.findByDeliveryIdAndNotificationHubUserIdAndNotificationHubTenantId(
                deliveryId, NotificationScope.user(null), NotificationScope.tenant())
            .orElseThrow(() -> new IllegalArgumentException("Delivery not found: " + deliveryId));

        deliveryRepository.delete(delivery);
    }

    public List<NotificationPreferenceResponse> updatePreferences(
        String userId,
        UpdatePreferencesRequest request
    ) {
        String recipient = NotificationScope.user(userId);
        String tenant = NotificationScope.tenant();
        preferenceRepository.deleteByUserIdAndTenantId(recipient, tenant);

        List<NotificationPreference> newPreferences = request.getPreferences().stream()
            .map(prefDto -> {
                NotificationPreference preference = new NotificationPreference();
                preference.setUserId(recipient);
                preference.setTenantId(tenant);
                preference.setCategory(prefDto.getCategory());
                preference.setEnabled(prefDto.isEnabled());
                preference.setEnabledChannels(prefDto.getChannels().stream()
                    .map(NotificationChannel::valueOf)
                    .collect(Collectors.toSet()));
                return preference;
            })
            .collect(Collectors.toList());

        List<NotificationPreference> saved = preferenceRepository.saveAll(newPreferences);

        return saved.stream()
            .map(this::toPreferenceResponse)
            .collect(Collectors.toList());
    }

    public NotificationTemplateResponse createTemplate(CreateTemplateRequest request) {
        NotificationTemplate template = new NotificationTemplate();
        template.setTemplateId(generateTemplateId());
        template.setName(request.getName());
        template.setTemplateType(request.getTemplateType());
        template.setChannel(NotificationChannel.valueOf(request.getChannel()));
        template.setSubjectTemplate(request.getSubjectTemplate());
        template.setBodyTemplate(request.getBodyTemplate());
        template.setLanguage(request.getLanguage());
        template.setEnabled(request.isEnabled());
        template.setMandatory(request.isMandatory());
        template.setVariablesDescription(request.getVariablesDescription());

        NotificationTemplate saved = templateRepository.save(template);
        return toTemplateResponse(saved);
    }

    public NotificationTemplateResponse updateTemplate(
        String templateId,
        UpdateTemplateRequest request
    ) {
        NotificationTemplate template = templateRepository.findByTemplateId(templateId)
            .orElseThrow(() -> new IllegalArgumentException("Template not found: " + templateId));

        if (request.getName() != null) template.setName(request.getName());
        if (request.getTemplateType() != null) template.setTemplateType(request.getTemplateType());
        if (request.getChannel() != null) template.setChannel(NotificationChannel.valueOf(request.getChannel()));
        if (request.getSubjectTemplate() != null) template.setSubjectTemplate(request.getSubjectTemplate());
        if (request.getBodyTemplate() != null) template.setBodyTemplate(request.getBodyTemplate());
        if (request.getLanguage() != null) template.setLanguage(request.getLanguage());
        if (request.isEnabled() != template.isEnabled()) template.setEnabled(request.isEnabled());
        if (request.isMandatory() != template.isMandatory()) template.setMandatory(request.isMandatory());
        if (request.getVariablesDescription() != null) {
            template.setVariablesDescription(request.getVariablesDescription());
        }

        NotificationTemplate updated = templateRepository.save(template);
        return toTemplateResponse(updated);
    }

    public void deleteTemplate(String templateId) {
        NotificationTemplate template = templateRepository.findByTemplateId(templateId)
            .orElseThrow(() -> new IllegalArgumentException("Template not found: " + templateId));

        templateRepository.delete(template);
    }

    private String generateTemplateId() {
        return "TMPL_" + uuidSupplier.get().toString().substring(0, 8).toUpperCase();
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
}
