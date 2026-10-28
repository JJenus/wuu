package com.surf.tracker.shared.security;

public final class TenantContext {

    private static final ThreadLocal<Long> CURRENT_ORG = new ThreadLocal<>();
    private static final ThreadLocal<Long> CURRENT_USER = new ThreadLocal<>();

    private TenantContext() {}

    public static void setCurrentOrgId(Long orgId) {
        CURRENT_ORG.set(orgId);
    }

    public static Long getCurrentOrgId() {
        return CURRENT_ORG.get();
    }

    public static void setCurrentUserId(Long userId) {
        CURRENT_USER.set(userId);
    }

    public static Long getCurrentUserId() {
        return CURRENT_USER.get();
    }

    public static void clear() {
        CURRENT_ORG.remove();
        CURRENT_USER.remove();
    }
}
