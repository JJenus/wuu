package com.surf.tracker.notification.api;

import com.surf.tracker.notification.api.dto.*;
import com.surf.tracker.notification.application.NotificationQueryService;
import com.surf.tracker.notification.application.NotificationCommandService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@Tag(name = "Notifications", description = "Notification management endpoints")
public class NotificationController {

    private final NotificationQueryService queryService;
    private final NotificationCommandService commandService;

    public NotificationController(
        NotificationQueryService queryService,
        NotificationCommandService commandService
    ) {
        this.queryService = queryService;
        this.commandService = commandService;
    }

    @GetMapping
    @Operation(summary = "Get notifications with filtering")
    public ResponseEntity<Page<NotificationResponse>> getNotifications(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) String alertId,
            Pageable pageable) {

        Page<NotificationResponse> notifications = queryService.findNotifications(
            userId, status, channel, alertId, pageable
        );
        return ResponseEntity.ok(notifications);
    }

    @GetMapping("/{deliveryId}")
    @Operation(summary = "Get notification by ID")
    public ResponseEntity<NotificationResponse> getNotificationById(
            @PathVariable String deliveryId) {

        NotificationResponse notification = queryService.getNotificationById(deliveryId);
        return ResponseEntity.ok(notification);
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "Get notifications for a specific user")
    public ResponseEntity<Page<NotificationResponse>> getUserNotifications(
            @PathVariable String userId,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            Pageable pageable) {

        Page<NotificationResponse> notifications = queryService.getUserNotifications(
            userId, unreadOnly, pageable
        );
        return ResponseEntity.ok(notifications);
    }

    @PostMapping("/{deliveryId}/read")
    @Operation(summary = "Mark notification as read")
    public ResponseEntity<Void> markAsRead(@PathVariable String deliveryId) {
        commandService.markAsRead(deliveryId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/user/{userId}/read-all")
    @Operation(summary = "Mark all user notifications as read")
    public ResponseEntity<Void> markAllAsRead(@PathVariable String userId) {
        commandService.markAllAsRead(userId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{deliveryId}")
    @Operation(summary = "Delete notification")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> deleteNotification(@PathVariable String deliveryId) {
        commandService.deleteDelivery(deliveryId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/preferences/{userId}")
    @Operation(summary = "Get notification preferences for user")
    public ResponseEntity<List<NotificationPreferenceResponse>> getUserPreferences(
            @PathVariable String userId) {

        List<NotificationPreferenceResponse> preferences =
            queryService.getUserPreferences(userId);
        return ResponseEntity.ok(preferences);
    }

    @PutMapping("/preferences/{userId}")
    @Operation(summary = "Update notification preferences")
    public ResponseEntity<List<NotificationPreferenceResponse>> updatePreferences(
            @PathVariable String userId,
            @Valid @RequestBody UpdatePreferencesRequest request) {

        List<NotificationPreferenceResponse> updated =
            commandService.updatePreferences(userId, request);
        return ResponseEntity.ok(updated);
    }

    @GetMapping("/templates")
    @Operation(summary = "Get notification templates")
    public ResponseEntity<Page<NotificationTemplateResponse>> getTemplates(
            @RequestParam(required = false) String templateType,
            @RequestParam(required = false) String channel,
            Pageable pageable) {

        Page<NotificationTemplateResponse> templates =
            queryService.getTemplates(templateType, channel, pageable);
        return ResponseEntity.ok(templates);
    }

    @PostMapping("/templates")
    @Operation(summary = "Create notification template")
    public ResponseEntity<NotificationTemplateResponse> createTemplate(
            @Valid @RequestBody CreateTemplateRequest request) {

        NotificationTemplateResponse template = commandService.createTemplate(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(template);
    }

    @PutMapping("/templates/{templateId}")
    @Operation(summary = "Update notification template")
    public ResponseEntity<NotificationTemplateResponse> updateTemplate(
            @PathVariable String templateId,
            @Valid @RequestBody UpdateTemplateRequest request) {

        NotificationTemplateResponse template =
            commandService.updateTemplate(templateId, request);
        return ResponseEntity.ok(template);
    }

    @DeleteMapping("/templates/{templateId}")
    @Operation(summary = "Delete notification template")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> deleteTemplate(@PathVariable String templateId) {
        commandService.deleteTemplate(templateId);
        return ResponseEntity.noContent().build();
    }
}