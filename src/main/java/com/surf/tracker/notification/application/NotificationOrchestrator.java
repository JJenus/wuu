package com.surf.tracker.notification.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.surf.tracker.notification.application.queue.NotificationMessage;
import com.surf.tracker.notification.application.queue.NotificationQueuePublisher;
import com.surf.tracker.notification.domain.entity.Delivery;
import com.surf.tracker.notification.domain.entity.DeliveryEvent;
import com.surf.tracker.notification.domain.entity.NotificationHub;
import com.surf.tracker.notification.domain.entity.NotificationTemplate;
import com.surf.tracker.notification.domain.enums.DeliveryStatus;
import com.surf.tracker.notification.domain.enums.NotificationChannel;
import com.surf.tracker.notification.domain.enums.NotificationStatus;
import com.surf.tracker.notification.infrastructure.repository.DeliveryEventRepository;
import com.surf.tracker.notification.infrastructure.repository.DeliveryRepository;
import com.surf.tracker.notification.infrastructure.repository.NotificationHubRepository;
import com.surf.tracker.notification.infrastructure.repository.NotificationTemplateRepository;
import com.surf.tracker.shared.events.AlertRaisedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

@Service
@Transactional
public class NotificationOrchestrator {

    private static final Logger logger = LoggerFactory.getLogger(NotificationOrchestrator.class);

    private final NotificationHubRepository hubRepository;
    private final NotificationTemplateRepository templateRepository;
    private final DeliveryRepository deliveryRepository;
    private final DeliveryEventRepository eventRepository;
    private final NotificationQueuePublisher queuePublisher;
    private final PreferenceResolver preferenceResolver;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Supplier<UUID> uuidSupplier;

    @org.springframework.beans.factory.annotation.Autowired
    public NotificationOrchestrator(
        NotificationHubRepository hubRepository,
        NotificationTemplateRepository templateRepository,
        DeliveryRepository deliveryRepository,
        DeliveryEventRepository eventRepository,
        NotificationQueuePublisher queuePublisher,
        PreferenceResolver preferenceResolver,
        ObjectMapper objectMapper,
        Clock clock
    ) {
        this(hubRepository, templateRepository, deliveryRepository, eventRepository,
             queuePublisher, preferenceResolver, objectMapper, clock, UUID::randomUUID);
    }

    NotificationOrchestrator(
        NotificationHubRepository hubRepository,
        NotificationTemplateRepository templateRepository,
        DeliveryRepository deliveryRepository,
        DeliveryEventRepository eventRepository,
        NotificationQueuePublisher queuePublisher,
        PreferenceResolver preferenceResolver,
        ObjectMapper objectMapper,
        Clock clock,
        Supplier<UUID> uuidSupplier
    ) {
        this.hubRepository = hubRepository;
        this.templateRepository = templateRepository;
        this.deliveryRepository = deliveryRepository;
        this.eventRepository = eventRepository;
        this.queuePublisher = queuePublisher;
        this.preferenceResolver = preferenceResolver;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.uuidSupplier = uuidSupplier;
    }

    public void processAlert(AlertRaisedEvent alert) {
        logger.info("Processing alert for notification: {}", alert.getAlertId());

        NotificationMessage message = new NotificationMessage(
            "admin",
            alert.getAlertId(),
            alert.getAlertType(),
            buildContextFromAlert(alert)
        );

        queuePublisher.publish(message);
        logger.info("Published notification message to queue for alert {}", alert.getAlertId());
    }

    public void processNotification(NotificationMessage message) {
        logger.info("Processing notification message for user {}", message.getUserId());

        NotificationTemplate template = templateRepository
            .findByTemplateId(message.getTemplateKey())
            .orElseGet(() -> getDefaultTemplate(message.getTemplateKey()));

        String category = template.getCategory() != null
            ? template.getCategory()
            : (message.getCategory() != null ? message.getCategory() : "DEFAULT");

        NotificationHub hub = new NotificationHub();
        hub.setNotificationId(uuidSupplier.get().toString());
        hub.setUserId(message.getUserId());
        hub.setTemplateId(template.getTemplateId());
        hub.setCategory(category);
        hub.setAlertId(message.getAlertId());
        hub.setMetadata(toJson(message.getContext()));
        hub.setStatus(NotificationStatus.CREATED);

        hubRepository.saveAndFlush(hub);

        Map<NotificationChannel, PreferenceResolver.ChannelPreference> prefs =
            preferenceResolver.resolvePreferences(message.getUserId(), category, template);

        List<Delivery> deliveries = new ArrayList<>();

        for (NotificationChannel channel : NotificationChannel.values()) {
            PreferenceResolver.ChannelPreference pref = prefs.get(channel);
            if (pref != null && pref.enabled()) {
                Delivery delivery = createDelivery(hub, channel, template, message.getContext());
                deliveries.add(delivery);

                DeliveryEvent event = DeliveryEvent.created(
                    delivery.getDeliveryId(),
                    "Created via queue for alert: " + message.getAlertId(),
                    clock.instant()
                );
                eventRepository.save(event);
            }
        }

        hub.setStatus(NotificationStatus.PROCESSING);

        for (Delivery delivery : deliveries) {
            hub.addDelivery(delivery);
        }
        hubRepository.save(hub);

        hub.recalculateStatus();
        hubRepository.save(hub);

        logger.info("Created notification hub {} with {} deliveries for user {}",
            hub.getNotificationId(), deliveries.size(), message.getUserId());
    }

    private Delivery createDelivery(NotificationHub hub, NotificationChannel channel,
                                   NotificationTemplate template, Map<String, Object> context) {
        Delivery delivery = new Delivery();
        delivery.setDeliveryId(uuidSupplier.get().toString());
        delivery.setChannel(channel);
        delivery.setTemplateId(template.getTemplateId());
        delivery.setTitle(renderTemplate(template.getSubjectTemplate(), context));
        delivery.setMessage(renderTemplate(template.getBodyTemplate(), context));
        delivery.setTemplateVariables(toJson(context));
        delivery.setRecipient(determineRecipient(hub.getUserId(), channel));
        delivery.setStatus(DeliveryStatus.PENDING);
        delivery.setMaxAttempts(5);
        return delivery;
    }

    private String determineRecipient(String userId, NotificationChannel channel) {
        return switch (channel) {
            case EMAIL -> userId + "@example.com";
            case SMS -> "+1234567890";
            default -> userId;
        };
    }

    private String renderTemplate(String template, Map<String, Object> context) {
        if (template == null) return "";
        String result = template;
        for (Map.Entry<String, Object> entry : context.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}",
                entry.getValue() != null ? entry.getValue().toString() : "");
        }
        return result;
    }

    private String toJson(Map<String, Object> context) {
        try {
            return objectMapper.writeValueAsString(context);
        } catch (Exception e) {
            return "{}";
        }
    }

    private Map<String, Object> buildContextFromAlert(AlertRaisedEvent alert) {
        Map<String, Object> context = new HashMap<>();
        context.put("alertId", alert.getAlertId());
        context.put("ruleKey", alert.getRuleKey());
        context.put("vehicleId", alert.getVehicleId());
        context.put("alertType", alert.getAlertType());
        context.put("severity", alert.getSeverity());
        context.put("message", alert.getMessage());
        context.put("timestamp", alert.getTimestamp() != null ? alert.getTimestamp().toString() : "");
        context.put("latitude", alert.getLatitude() != null ? alert.getLatitude().toString() : "");
        context.put("longitude", alert.getLongitude() != null ? alert.getLongitude().toString() : "");
        return context;
    }

    private NotificationTemplate getDefaultTemplate(String templateKey) {
        NotificationTemplate template = new NotificationTemplate();
        template.setTemplateId(templateKey);
        template.setName("Default Template");
        template.setTemplateType(templateKey);
        template.setChannel(NotificationChannel.EMAIL);
        template.setSubjectTemplate("Alert: {{alertType}}");
        template.setBodyTemplate("{{message}}\\n\\nVehicle: {{vehicleId}}\\nTime: {{timestamp}}");
        template.setEnabled(true);
        template.setMandatory(false);
        return template;
    }
}
