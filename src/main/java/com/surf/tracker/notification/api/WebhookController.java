package com.surf.tracker.notification.api;

import com.surf.tracker.main.api.ApiV1;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.surf.tracker.notification.api.dto.WebhookEmailPayload;
import com.surf.tracker.notification.api.dto.WebhookPushPayload;
import com.surf.tracker.notification.domain.entity.Delivery;
import com.surf.tracker.notification.domain.entity.DeliveryEvent;
import com.surf.tracker.notification.domain.entity.Device;
import com.surf.tracker.notification.domain.enums.DeliveryEventType;
import com.surf.tracker.notification.domain.enums.DeliveryStatus;
import com.surf.tracker.notification.domain.enums.ErrorType;
import com.surf.tracker.notification.infrastructure.repository.DeliveryEventRepository;
import com.surf.tracker.notification.infrastructure.repository.DeliveryRepository;
import com.surf.tracker.notification.infrastructure.repository.DeviceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

@RestController
@ApiV1
@RequestMapping("/notifications/webhooks")
@Tag(name = "Disabled Webhooks", description = "Mapped legacy callbacks are denied by HTTP security; not enabled provider integrations.")
public class WebhookController {

    private static final Logger logger = LoggerFactory.getLogger(WebhookController.class);

    private final DeliveryRepository deliveryRepository;
    private final DeliveryEventRepository eventRepository;
    private final DeviceRepository deviceRepository;
    private final Clock clock;

    public WebhookController(DeliveryRepository deliveryRepository,
                            DeliveryEventRepository eventRepository,
                            DeviceRepository deviceRepository,
                            Clock clock) {
        this.deliveryRepository = deliveryRepository;
        this.eventRepository = eventRepository;
        this.deviceRepository = deviceRepository;
        this.clock = clock;
    }

    @PostMapping("/push/{provider}")
    @Operation(operationId = "webhooksPush", summary = "Disabled push-provider callback", description = "SecurityConfig denies this HTTP operation for every caller. No provider-signature verification or replay protection is established here; do not configure a provider to use it as an enabled integration. The legacy handler would mutate delivery/device state, so the deny rule must remain.", responses = @ApiResponse(responseCode = "403", description = "Webhook HTTP access explicitly disabled"))
    public ResponseEntity<Void> handlePushCallback(
            @PathVariable String provider,
            @RequestBody WebhookPushPayload payload) {

        logger.debug("Received push callback from provider {}", provider);

        String deliveryId = payload.deliveryId();
        String status = payload.status();
        String errorCode = payload.errorCode();
        String errorMessage = payload.errorMessage();

        if (deliveryId == null) {
            logger.warn("Push callback missing deliveryId");
            return ResponseEntity.badRequest().build();
        }

        Optional<Delivery> deliveryOpt = deliveryRepository.findByDeliveryId(deliveryId);
        if (deliveryOpt.isEmpty()) {
            logger.warn("Delivery not found for push callback: {}", deliveryId);
            return ResponseEntity.notFound().build();
        }

        Delivery delivery = deliveryOpt.get();

        if ("DELIVERED".equalsIgnoreCase(status)) {
            Instant now = clock.instant();
            delivery.setStatus(DeliveryStatus.DELIVERED);
            delivery.setDeliveredAt(now);
            deliveryRepository.save(delivery);
            eventRepository.save(DeliveryEvent.delivered(deliveryId, null, now));
        } else if ("OPENED".equalsIgnoreCase(status)) {
            eventRepository.save(DeliveryEvent.opened(deliveryId, null, clock.instant()));
        } else if ("CLICKED".equalsIgnoreCase(status)) {
            eventRepository.save(DeliveryEvent.clicked(deliveryId, null, clock.instant()));
        } else if ("FAILED".equalsIgnoreCase(status) || "ERROR".equalsIgnoreCase(status)) {
            Instant now = clock.instant();
            ErrorType errorType = classifyPushError(errorCode);
            delivery.setStatus(DeliveryStatus.FAILED);
            delivery.setLastError(errorMessage != null ? errorMessage : "Provider error");
            delivery.setLastErrorType(errorType);
            delivery.incrementAttemptCount();
            deliveryRepository.save(delivery);

            eventRepository.save(DeliveryEvent.failed(deliveryId, errorMessage, now));

            if (errorType == ErrorType.PERMANENT && delivery.getDeviceId() != null) {
                deviceRepository.findById(delivery.getDeviceId()).ifPresent(device -> {
                    device.markInvalid();
                    deviceRepository.save(device);
                    logger.info("Marked device {} invalid", device.getDeviceId());
                });
            }
        }

        return ResponseEntity.ok().build();
    }

    @PostMapping("/email/{provider}")
    @Operation(operationId = "webhooksEmail", summary = "Disabled email-provider callback", description = "SecurityConfig denies this HTTP operation for every caller. Provider authenticity, replay safety and idempotency must be implemented and verified separately before enabling it. The legacy payload handler is not proof of a working provider integration.", responses = @ApiResponse(responseCode = "403", description = "Webhook HTTP access explicitly disabled"))
    public ResponseEntity<Void> handleEmailCallback(
            @PathVariable String provider,
            @RequestBody WebhookEmailPayload payload) {

        logger.debug("Received email callback from provider {}", provider);

        String deliveryId = payload.deliveryId();
        String status = payload.status();
        String errorMessage = payload.errorMessage();

        if (deliveryId == null) {
            return ResponseEntity.badRequest().build();
        }

        Optional<Delivery> deliveryOpt = deliveryRepository.findByDeliveryId(deliveryId);
        if (deliveryOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Delivery delivery = deliveryOpt.get();

        if ("DELIVERED".equalsIgnoreCase(status)) {
            Instant now = clock.instant();
            delivery.setStatus(DeliveryStatus.DELIVERED);
            delivery.setDeliveredAt(now);
            deliveryRepository.save(delivery);
            eventRepository.save(DeliveryEvent.delivered(deliveryId, null, now));
        } else if ("OPENED".equalsIgnoreCase(status)) {
            eventRepository.save(DeliveryEvent.opened(deliveryId, null, clock.instant()));
        } else if ("FAILED".equalsIgnoreCase(status)) {
            Instant now = clock.instant();
            ErrorType errorType = classifyEmailError(errorMessage);
            delivery.setStatus(DeliveryStatus.FAILED);
            delivery.setLastError(errorMessage);
            delivery.setLastErrorType(errorType);
            delivery.incrementAttemptCount();
            deliveryRepository.save(delivery);
            eventRepository.save(DeliveryEvent.failed(deliveryId, errorMessage, now));
        }

        return ResponseEntity.ok().build();
    }

    private ErrorType classifyPushError(String errorCode) {
        if (errorCode == null) return ErrorType.TRANSIENT;
        if (errorCode.contains("InvalidRegistration") || errorCode.contains("NotRegistered")) {
            return ErrorType.PERMANENT;
        }
        return ErrorType.TRANSIENT;
    }

    private ErrorType classifyEmailError(String errorMessage) {
        if (errorMessage == null) return ErrorType.TRANSIENT;
        String lower = errorMessage.toLowerCase();
        if (lower.contains("hard bounce") || lower.contains("invalid") || lower.contains("blocked")) {
            return ErrorType.PERMANENT;
        }
        return ErrorType.TRANSIENT;
    }
}
