package com.surf.tracker.alerting.infrastructure.cache;

import com.surf.tracker.alerting.domain.entity.TrackerAlert;
import com.surf.tracker.shared.redis.RedisKeyScanner;
import com.surf.tracker.shared.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class AlertCacheService {

    private static final Logger logger = LoggerFactory.getLogger(AlertCacheService.class);

    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisKeyGenerator keyGenerator;

    public AlertCacheService(
            RedisTemplate<String, Object> redisTemplate,
            RedisKeyGenerator keyGenerator) {
        this.redisTemplate = redisTemplate;
        this.keyGenerator = keyGenerator;
    }

    // Cache individual alerts
    public void cacheAlert(TrackerAlert alert) {
        try {
            if (TransactionalCacheInvalidation.defer(redisTemplate,
                    keyGenerator.getAllAlertKeysPattern(alert.getOrganizationId()))) return;
            String cacheKey = keyGenerator.getAlertDetailKey(alert.getAlertId(), alert.getOrganizationId());
            redisTemplate.opsForValue().set(
                    cacheKey,
                    alert,
                    1, // 1 hour TTL for individual alerts
                    TimeUnit.HOURS
            );
        } catch (Exception e) {
            logger.error("Failed to cache alert: {}", alert.getAlertId(), e);
        }
    }

    public Optional<TrackerAlert> getAlertById(Long alertId) {
        try {
            String cacheKey = keyGenerator.getAlertDetailKey(alertId);
            TrackerAlert alert = (TrackerAlert) redisTemplate.opsForValue().get(cacheKey);
            return Optional.ofNullable(alert).filter(value -> value.getOrganizationId() != null
                && value.getOrganizationId().equals(TenantContext.getCurrentOrgId()));
        } catch (Exception e) {
            logger.error("Failed to get alert from cache: {}", alertId, e);
            return Optional.empty();
        }
    }

    // Cache active alerts for vehicle
    public void cacheActiveVehicleAlerts(String vehicleId, List<TrackerAlert> alerts) {
        try {
            if (TransactionalCacheInvalidation.defer(redisTemplate, keyGenerator.getAllAlertKeysPattern())) return;
            String cacheKey = keyGenerator.getActiveVehicleAlertsKey(vehicleId);
            if (alerts.stream().anyMatch(alert -> !java.util.Objects.equals(
                    alert.getOrganizationId(), TenantContext.getCurrentOrgId()))) return;
            redisTemplate.delete(cacheKey);

            if (!alerts.isEmpty()) {
                redisTemplate.opsForList().rightPushAll(cacheKey, alerts.toArray());
                redisTemplate.expire(cacheKey, 30, TimeUnit.MINUTES); // 30 minute TTL
            }
        } catch (Exception e) {
            logger.error("Failed to cache active alerts for vehicle: {}", vehicleId, e);
        }
    }

    public List<TrackerAlert> getActiveVehicleAlerts(String vehicleId) {
        try {
            String cacheKey = keyGenerator.getActiveVehicleAlertsKey(vehicleId);
            List<Object> cached = redisTemplate.opsForList().range(cacheKey, 0, -1);

            if (cached != null && !cached.isEmpty()) {
                if (cached.stream().anyMatch(value -> !(value instanceof TrackerAlert alert)
                        || !java.util.Objects.equals(alert.getOrganizationId(), TenantContext.getCurrentOrgId()))) {
                    return null;
                }
                return cached.stream()
                        .map(obj -> (TrackerAlert) obj)
                        .collect(Collectors.toList());
            }
            return null; // Cache miss
        } catch (Exception e) {
            logger.error("Failed to get active alerts from cache: {}", vehicleId, e);
            return null;
        }
    }

    // Cache statistics
    public void cacheAlertStatistics(String key, Object statistics) {
        try {
            if (TransactionalCacheInvalidation.defer(redisTemplate, keyGenerator.getAllAlertKeysPattern())) return;
            redisTemplate.opsForValue().set(
                    keyGenerator.getAlertStatisticsKey(key),
                    statistics,
                    5, // 5 minutes TTL for statistics
                    TimeUnit.MINUTES
            );
        } catch (Exception e) {
            logger.error("Failed to cache alert statistics: {}", key, e);
        }
    }

    public Optional<Object> getAlertStatistics(String key) {
        try {
            Object stats = redisTemplate.opsForValue()
                    .get(keyGenerator.getAlertStatisticsKey(key));
            return Optional.ofNullable(stats);
        } catch (Exception e) {
            logger.error("Failed to get alert statistics from cache: {}", key, e);
            return Optional.empty();
        }
    }

    // Invalidation methods
    public void invalidateAlert(Long alertId) {
        try {
            if (TransactionalCacheInvalidation.defer(redisTemplate, keyGenerator.getAllAlertKeysPattern())) return;
            String detailKey = keyGenerator.getAlertDetailKey(alertId);
            redisTemplate.delete(detailKey);

            // Also invalidate any lists that might contain this alert
            // This is simpler than trying to remove from lists
        } catch (Exception e) {
            logger.error("Failed to invalidate alert cache: {}", alertId, e);
        }
    }

    public void invalidateVehicleAlerts(String vehicleId) {
        try {
            if (TransactionalCacheInvalidation.defer(redisTemplate, keyGenerator.getAllAlertKeysPattern())) return;
            String activeKey = keyGenerator.getActiveVehicleAlertsKey(vehicleId);
            String recentKey = keyGenerator.getRecentVehicleAlertsKey(vehicleId);
            redisTemplate.delete(java.util.Arrays.asList(activeKey, recentKey));
        } catch (Exception e) {
            logger.error("Failed to invalidate vehicle alert cache: {}", vehicleId, e);
        }
    }

    public void invalidateStatistics() {
        try {
            if (TransactionalCacheInvalidation.defer(redisTemplate, keyGenerator.getAllAlertKeysPattern())) return;
            String pattern = keyGenerator.getAlertStatisticsPattern();
            List<String> keys = RedisKeyScanner.scanKeys(redisTemplate, pattern);
            if (!keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception e) {
            logger.error("Failed to invalidate statistics cache", e);
        }
    }

    // Clear all alert cache
    public void clearAll() {
        try {
            if (TransactionalCacheInvalidation.defer(redisTemplate, keyGenerator.getAllAlertKeysPattern())) return;
            String pattern = keyGenerator.getAllAlertKeysPattern();
            List<String> keys = RedisKeyScanner.scanKeys(redisTemplate, pattern);
            if (!keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception e) {
            logger.error("Failed to clear alert cache", e);
        }
    }
}
