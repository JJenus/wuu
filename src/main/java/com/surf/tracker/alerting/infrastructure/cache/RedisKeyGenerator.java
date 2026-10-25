package com.surf.tracker.alerting.infrastructure.cache;

import org.springframework.stereotype.Component;
import com.surf.tracker.shared.security.TenantContext;

@Component
public class RedisKeyGenerator {

    private static final String PREFIX = "alerting:";

    // ========== ALERT CACHE KEYS ==========
    private static final String ALERT_PREFIX = PREFIX + "alert:";
    public static final String ALERT_DETAIL_KEY_PREFIX = ALERT_PREFIX + "detail:";
    public static final String ACTIVE_VEHICLE_ALERTS_KEY_PREFIX = ALERT_PREFIX + "vehicle:active:";
    public static final String RECENT_VEHICLE_ALERTS_KEY_PREFIX = ALERT_PREFIX + "vehicle:recent:";
    public static final String ALERT_STATISTICS_KEY_PREFIX = ALERT_PREFIX + "stats:";

    // ========== ALERT RULE CACHE KEYS ==========
    private static final String RULE_PREFIX = PREFIX + "rule:";
    public static final long RULE_DETAIL_TTL = 3600L;
    public static final long VEHICLE_RULE_CACHE_TTL = 300L;
    public static final long INDEX_CACHE_TTL = 600L;

    // ========== GEOFENCE CACHE KEYS ==========
    private static final String GEOFENCE_PREFIX = PREFIX + "geofence:";
    public static final String GEOFENCE_DETAIL_KEY_PREFIX = GEOFENCE_PREFIX + "detail:";
    public static final String VEHICLE_GEOFENCES_KEY_PREFIX = GEOFENCE_PREFIX + "vehicle:";
    public static final String ACTIVE_VEHICLE_GEOFENCES_KEY_PREFIX = GEOFENCE_PREFIX + "vehicle:active:";
    public static final String PAGINATED_GEOFENCES_KEY_PREFIX = GEOFENCE_PREFIX + "paginated:";
    public static final long GEOFENCE_CACHE_TTL = 3600L;
    public static final long VEHICLE_GEOFENCE_CACHE_TTL = 600L;
    public static final long PAGINATION_CACHE_TTL = 300L;

    // ========== ALERT RULE METHODS ==========
    public String getRuleDetailKey(String ruleKey) {
        return getRuleDetailKey(ruleKey, TenantContext.getCurrentOrgId());
    }

    public String getRuleDetailKey(String ruleKey, Long organizationId) {
        return rulePrefix(organizationId) + "detail:" + ruleKey;
    }

    public String getAllActiveRulesKey() {
        return getAllActiveRulesKey(TenantContext.getCurrentOrgId());
    }

    public String getAllActiveRulesKey(Long organizationId) {
        return rulePrefix(organizationId) + "active:all";
    }

    public String getVehicleRulesKey(String vehicleId) {
        return getVehicleRulesKey(vehicleId, TenantContext.getCurrentOrgId());
    }

    public String getVehicleRulesKey(String vehicleId, Long organizationId) {
        return rulePrefix(organizationId) + "vehicle:" + vehicleId;
    }

    public String getVehiclesWithRulesKey() {
        return getVehiclesWithRulesKey(TenantContext.getCurrentOrgId());
    }

    public String getVehiclesWithRulesKey(Long organizationId) {
        return rulePrefix(organizationId) + "vehicles:with:rules";
    }

    public String getAllRuleKeysPattern() {
        return getAllRuleKeysPattern(TenantContext.getCurrentOrgId());
    }

    public String getAllRuleKeysPattern(Long organizationId) {
        return rulePrefix(organizationId) + "*";
    }

    // ========== ALERT METHODS ==========
    public String getAlertDetailKey(Long alertId) {
        return getAlertDetailKey(alertId, TenantContext.getCurrentOrgId());
    }

    public String getAlertDetailKey(Long alertId, Long organizationId) {
        return tenantPrefix(ALERT_PREFIX, organizationId) + "detail:" + alertId;
    }

    public String getActiveVehicleAlertsKey(String vehicleId) {
        return tenantPrefix(ALERT_PREFIX, TenantContext.getCurrentOrgId()) + "vehicle:active:" + vehicleId;
    }

    public String getRecentVehicleAlertsKey(String vehicleId) {
        return tenantPrefix(ALERT_PREFIX, TenantContext.getCurrentOrgId()) + "vehicle:recent:" + vehicleId;
    }

    public String getAlertStatisticsKey(String key) {
        return tenantPrefix(ALERT_PREFIX, TenantContext.getCurrentOrgId()) + "stats:" + key;
    }

    public String getAlertStatisticsPattern() {
        return tenantPrefix(ALERT_PREFIX, TenantContext.getCurrentOrgId()) + "stats:*";
    }

    public String getAllAlertKeysPattern() {
        return getAllAlertKeysPattern(TenantContext.getCurrentOrgId());
    }

    public String getAllAlertKeysPattern(Long organizationId) {
        return tenantPrefix(ALERT_PREFIX, organizationId) + "*";
    }

    // ========== GEOFENCE METHODS ==========
    public String getGeofenceDetailKey(Long geofenceId) {
        return getGeofenceDetailKey(geofenceId, TenantContext.getCurrentOrgId());
    }

    public String getGeofenceDetailKey(Long geofenceId, Long organizationId) {
        return tenantPrefix(GEOFENCE_PREFIX, organizationId) + "detail:" + geofenceId;
    }

    public String getGeofenceDetailKey(String geofenceId) {
        return tenantPrefix(GEOFENCE_PREFIX, TenantContext.getCurrentOrgId()) + "detail:" + geofenceId;
    }

    public String getVehicleGeofencesKey(String vehicleId) {
        return getVehicleGeofencesKey(vehicleId, TenantContext.getCurrentOrgId());
    }

    public String getVehicleGeofencesKey(String vehicleId, Long organizationId) {
        return tenantPrefix(GEOFENCE_PREFIX, organizationId) + "vehicle:" + vehicleId;
    }

    public String getActiveVehicleGeofencesKey(String vehicleId) {
        return getActiveVehicleGeofencesKey(vehicleId, TenantContext.getCurrentOrgId());
    }

    public String getActiveVehicleGeofencesKey(String vehicleId, Long organizationId) {
        return tenantPrefix(GEOFENCE_PREFIX, organizationId) + "vehicle:active:" + vehicleId;
    }

    public String getAllGeofenceKeysPattern() {
        return getAllGeofenceKeysPattern(TenantContext.getCurrentOrgId());
    }

    public String getAllGeofenceKeysPattern(Long organizationId) {
        return tenantPrefix(GEOFENCE_PREFIX, organizationId) + "*";
    }

    public String getPaginatedGeofencesKey(int page, int size, String sortBy, String sortDir,
                                            String search, String vehicleId, Boolean active) {
        StringBuilder query = new StringBuilder();
        for (Object field : java.util.Arrays.asList(page, size, sortBy, sortDir, search, vehicleId, active)) {
            String value = field == null ? null : field.toString();
            query.append(value == null ? "-1:" : value.length() + ":" + value);
        }
        return tenantPrefix(GEOFENCE_PREFIX, TenantContext.getCurrentOrgId()) + "paginated:" + query;
    }

    public String getPaginatedGeofencesPattern() {
        return tenantPrefix(GEOFENCE_PREFIX, TenantContext.getCurrentOrgId()) + "paginated:*";
    }

    public String getPaginatedRulesKey(Integer page, Integer size, String sortBy, String sortDir,
                                        String search, String ruleType, Boolean enabled) {
        StringBuilder query = new StringBuilder();
        for (Object field : java.util.Arrays.asList(page, size, sortBy, sortDir, search, ruleType, enabled)) {
            if (field == null) {
                query.append("-1:");
            } else {
                String value = field.toString();
                query.append(value.length()).append(':').append(value);
            }
        }
        return rulePrefix(TenantContext.getCurrentOrgId()) + "paginated:" + query;
    }

    public String getPaginatedRulesPattern() {
        return rulePrefix(TenantContext.getCurrentOrgId()) + "paginated:*";
    }

    private String rulePrefix(Long organizationId) {
        if (organizationId == null || organizationId <= 0) {
            throw new IllegalStateException("Organization context is required for alert-rule caching");
        }
        return RULE_PREFIX + "v2:org:" + organizationId + ":";
    }

    private String tenantPrefix(String family, Long organizationId) {
        if (organizationId == null || organizationId <= 0) {
            throw new IllegalStateException("Organization context is required for alert caching");
        }
        return family + "v2:org:" + organizationId + ":";
    }
}
