package com.surf.tracker.notification.api;

import com.surf.tracker.main.api.ApiV1;

import com.surf.tracker.notification.api.dto.*;
import com.surf.tracker.notification.application.NotificationQueryService;
import com.surf.tracker.notification.application.NotificationCommandService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springdoc.core.annotations.ParameterObject;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

@RestController
@ApiV1
@RequestMapping("/notifications")
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
    @Operation(operationId = "notificationsSearch", summary = "Search notifications", description = "Requires an authenticated bearer session and active tenant. userId must be omitted or equal to the authenticated database user ID; other recipients are denied. status and channel use exact enum names; alertId is optional. Uses Spring Pageable page/size/sort and returns Spring Page, not the project's PagedResponse.")
    public ResponseEntity<Page<NotificationResponse>> getNotifications(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) String alertId,
            @ParameterObject Pageable pageable) {

        Page<NotificationResponse> notifications = queryService.findNotifications(
            userId, status, channel, alertId, pageable
        );
        return ResponseEntity.ok(notifications);
    }

    @GetMapping("/{deliveryId}")
    @Operation(operationId = "notificationsGet", summary = "Get a notification delivery", description = "Requires an authenticated bearer session. deliveryId is the string delivery identifier. Service-level access checks remain authoritative; this operation reads persisted delivery state, not provider status directly.")
    public ResponseEntity<NotificationResponse> getNotificationById(
            @PathVariable String deliveryId) {

        NotificationResponse notification = queryService.getNotificationById(deliveryId);
        return ResponseEntity.ok(notification);
    }

    @GetMapping("/user/{userId}")
    @Operation(operationId = "notificationsUserPage", summary = "Get user notifications", description = "Requires an authenticated bearer session and active tenant; userId must equal the authenticated database user ID. Uses Spring Pageable and returns Spring Page. unreadOnly=true excludes deliveries with a persisted OPENED event before pagination and total counts. Provider delivery status is independent of read state; sent/delivered/failed events do not mark a delivery read. Default false includes both read and unread deliveries.")
    public ResponseEntity<Page<NotificationResponse>> getUserNotifications(
            @PathVariable String userId,
            @io.swagger.v3.oas.annotations.Parameter(description = "Return only deliveries without an OPENED event. Filters both page content and totals; independent of delivery status.")
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @ParameterObject Pageable pageable) {

        Page<NotificationResponse> notifications = queryService.getUserNotifications(
            userId, unreadOnly, pageable
        );
        return ResponseEntity.ok(notifications);
    }

    @PostMapping("/{deliveryId}/read")
    @Operation(operationId = "notificationsMarkRead", summary = "Mark a notification as read", description = "Requires an authenticated bearer session and service-authorized access to deliveryId. Does not require a JSON body. Success returns HTTP 200 without a response body; it does not acknowledge an underlying alert.")
    public ResponseEntity<Void> markAsRead(@PathVariable String deliveryId) {
        commandService.markAsRead(deliveryId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/user/{userId}/read-all")
    @Operation(operationId = "notificationsMarkAllRead", summary = "Mark user notifications as read", description = "Requires an authenticated bearer session and service-authorized access to userId. Marks notifications read through the command service; success is HTTP 200 with no body. Alert acknowledgement/resolution is separate.")
    public ResponseEntity<Void> markAllAsRead(@PathVariable String userId) {
        commandService.markAllAsRead(userId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{deliveryId}")
    @Operation(operationId = "notificationsDelete", summary = "Delete a notification delivery", description = "Requires an authenticated bearer session and service-authorized access to deliveryId. Deletes the delivery through the command service. Success is 204 with no body.")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> deleteNotification(@PathVariable String deliveryId) {
        commandService.deleteDelivery(deliveryId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/preferences/{userId}")
    @Operation(operationId = "notificationsPreferencesGet", summary = "Get user notification preferences", description = "Requires an authenticated bearer session and active tenant. userId must equal the authenticated database user ID. Returns a JSON array, not a paginated envelope. This does not imply that every provider channel is configured.")
    public ResponseEntity<List<NotificationPreferenceResponse>> getUserPreferences(
            @PathVariable String userId) {

        List<NotificationPreferenceResponse> preferences =
            queryService.getUserPreferences(userId);
        return ResponseEntity.ok(preferences);
    }

    @PutMapping("/preferences/{userId}")
    @Operation(operationId = "notificationsPreferencesUpdate", summary = "Update notification preferences", description = "Requires an authenticated bearer session. Validates UpdatePreferencesRequest and applies preferences through the command service for authorized userId. Returns the updated preference array; this is not provider provisioning or proof of message delivery.")
    public ResponseEntity<List<NotificationPreferenceResponse>> updatePreferences(
            @PathVariable String userId,
            @Valid @RequestBody UpdatePreferencesRequest request) {

        List<NotificationPreferenceResponse> updated =
            commandService.updatePreferences(userId, request);
        return ResponseEntity.ok(updated);
    }

    @GetMapping("/templates")
    @Operation(operationId = "notificationsTemplatesSearch", summary = "Search notification templates", description = "Requires system.admin, not merely an authenticated user. Optional templateType and channel filters use Spring Pageable page/size/sort parameters. Returns Spring Page of template responses.")
    public ResponseEntity<Page<NotificationTemplateResponse>> getTemplates(
            @RequestParam(required = false) String templateType,
            @RequestParam(required = false) String channel,
            @ParameterObject Pageable pageable) {

        Page<NotificationTemplateResponse> templates =
            queryService.getTemplates(templateType, channel, pageable);
        return ResponseEntity.ok(templates);
    }

    @PostMapping("/templates")
    @Operation(operationId = "notificationsTemplatesCreate", summary = "Create a notification template", description = "Requires system.admin. Validates CreateTemplateRequest and persists a template. Template creation does not send a notification or verify provider credentials.")
    @ApiResponse(responseCode = "201", description = "Notification template created")
    public ResponseEntity<NotificationTemplateResponse> createTemplate(
            @Valid @RequestBody CreateTemplateRequest request) {

        NotificationTemplateResponse template = commandService.createTemplate(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(template);
    }

    @PutMapping("/templates/{templateId}")
    @Operation(operationId = "notificationsTemplatesUpdate", summary = "Update a notification template", description = "Requires system.admin. Uses string templateId and validated UpdateTemplateRequest fields. Returns the saved template; does not resend previously created deliveries.")
    public ResponseEntity<NotificationTemplateResponse> updateTemplate(
            @PathVariable String templateId,
            @Valid @RequestBody UpdateTemplateRequest request) {

        NotificationTemplateResponse template =
            commandService.updateTemplate(templateId, request);
        return ResponseEntity.ok(template);
    }

    @DeleteMapping("/templates/{templateId}")
    @Operation(operationId = "notificationsTemplatesDelete", summary = "Delete a notification template", description = "Requires system.admin. Deletes by string templateId through the command service. Success is 204 without a response body.")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> deleteTemplate(@PathVariable String templateId) {
        commandService.deleteTemplate(templateId);
        return ResponseEntity.noContent().build();
    }
}
