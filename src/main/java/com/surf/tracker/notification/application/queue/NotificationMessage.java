package com.surf.tracker.notification.application.queue;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class NotificationMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    private String userId;
    private String alertId;
    private String templateKey;
    private Map<String, Object> context = new HashMap<>();
    private String tenantId;
    private String category;

    public NotificationMessage() {
    }

    public NotificationMessage(String userId, String alertId, String templateKey,
                               Map<String, Object> context) {
        this.userId = userId;
        this.alertId = alertId;
        this.templateKey = templateKey;
        this.context = context != null ? context : new HashMap<>();
    }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getAlertId() { return alertId; }
    public void setAlertId(String alertId) { this.alertId = alertId; }

    public String getTemplateKey() { return templateKey; }
    public void setTemplateKey(String templateKey) { this.templateKey = templateKey; }

    public Map<String, Object> getContext() { return context; }
    public void setContext(Map<String, Object> context) { this.context = context; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
}