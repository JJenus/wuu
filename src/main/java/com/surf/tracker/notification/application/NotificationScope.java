package com.surf.tracker.notification.application;

import com.surf.tracker.shared.security.TenantContext;
import org.springframework.security.access.AccessDeniedException;

/** HTTP notification operations are always tenant-scoped and self-service. */
final class NotificationScope {
    private NotificationScope() {}

    static String user(String requested) {
        Long userId = TenantContext.getCurrentUserId();
        if (userId == null || TenantContext.getCurrentOrgId() == null
                || requested != null && !userId.toString().equals(requested)) {
            throw new AccessDeniedException("Notification access denied");
        }
        return userId.toString();
    }

    static String tenant() {
        user(null);
        return TenantContext.getCurrentOrgId().toString();
    }
}
