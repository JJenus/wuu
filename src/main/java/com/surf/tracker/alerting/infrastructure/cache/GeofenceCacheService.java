package com.surf.tracker.alerting.infrastructure.cache;

import com.surf.tracker.alerting.domain.entity.Geofence;
import com.surf.tracker.shared.redis.RedisKeyScanner;
import com.surf.tracker.shared.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class GeofenceCacheService {

    private static final Logger logger = LoggerFactory.getLogger(GeofenceCacheService.class);

    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisKeyGenerator keyGenerator;

    public GeofenceCacheService(
            RedisTemplate<String, Object> redisTemplate,
            RedisKeyGenerator keyGenerator) {
        this.redisTemplate = redisTemplate;
        this.keyGenerator = keyGenerator;
    }

    public void cacheGeofence(Geofence geofence) {
        try {
            if (TransactionalCacheInvalidation.defer(redisTemplate,
                    keyGenerator.getAllGeofenceKeysPattern(geofence.getOrganizationId()))) return;
            String detailKey = keyGenerator.getGeofenceDetailKey(geofence.getGeofenceId(), geofence.getOrganizationId());
            redisTemplate.opsForValue().set(
                    detailKey,
                    geofence,
                    RedisKeyGenerator.GEOFENCE_CACHE_TTL,
                    TimeUnit.SECONDS
            );

            // Invalidate vehicle caches
            for (String vehicleId : geofence.getVehicleIds()) {
                redisTemplate.delete(Arrays.asList(
                    keyGenerator.getVehicleGeofencesKey(vehicleId, geofence.getOrganizationId()),
                    keyGenerator.getActiveVehicleGeofencesKey(vehicleId, geofence.getOrganizationId())));
            }

        } catch (Exception e) {
            logger.error("Failed to cache geofence: {}", geofence.getGeofenceId(), e);
        }
    }

    public Optional<Geofence> getGeofenceById(Long geofenceId) {
        try {
            String cacheKey = keyGenerator.getGeofenceDetailKey(geofenceId);
            Geofence geofence = (Geofence) redisTemplate.opsForValue().get(cacheKey);
            if (geofence != null && !java.util.Objects.equals(
                    geofence.getOrganizationId(), TenantContext.getCurrentOrgId())) return Optional.empty();

            if (geofence != null) {
                // Refresh TTL on access
                redisTemplate.expire(cacheKey,
                        RedisKeyGenerator.GEOFENCE_CACHE_TTL,
                        TimeUnit.SECONDS);
            }

            return Optional.ofNullable(geofence);

        } catch (Exception e) {
            logger.error("Failed to get geofence from cache: {}", geofenceId, e);
            return Optional.empty();
        }
    }

    public void cacheVehicleGeofences(String vehicleId, List<Geofence> geofences) {
        try {
            if (TransactionalCacheInvalidation.defer(redisTemplate, keyGenerator.getAllGeofenceKeysPattern())) return;
            String cacheKey = keyGenerator.getVehicleGeofencesKey(vehicleId);
            if (geofences.stream().anyMatch(geofence -> !java.util.Objects.equals(
                    geofence.getOrganizationId(), TenantContext.getCurrentOrgId()))) return;

            // Delete existing cache
            redisTemplate.delete(cacheKey);

            if (!geofences.isEmpty()) {
                // Store as list
                redisTemplate.opsForList().rightPushAll(cacheKey, geofences.toArray());
                redisTemplate.expire(cacheKey,
                        RedisKeyGenerator.VEHICLE_GEOFENCE_CACHE_TTL,
                        TimeUnit.SECONDS);
            }

        } catch (Exception e) {
            logger.error("Failed to cache vehicle geofences: {}", vehicleId, e);
        }
    }

    public List<Geofence> getVehicleGeofences(String vehicleId) {
        try {
            String cacheKey = keyGenerator.getVehicleGeofencesKey(vehicleId);
            List<Object> cached = redisTemplate.opsForList().range(cacheKey, 0, -1);

            if (cached != null && !cached.isEmpty()) {
                if (cached.stream().anyMatch(value -> !(value instanceof Geofence geofence)
                        || !java.util.Objects.equals(geofence.getOrganizationId(), TenantContext.getCurrentOrgId()))) {
                    return null;
                }
                return cached.stream()
                        .map(obj -> (Geofence) obj)
                        .collect(Collectors.toList());
            }

            return null; // Indicate cache miss

        } catch (Exception e) {
            logger.error("Failed to get vehicle geofences from cache: {}", vehicleId, e);
            return null;
        }
    }

    public void invalidateGeofence(Long geofenceId) {
        try {
            if (TransactionalCacheInvalidation.defer(redisTemplate, keyGenerator.getAllGeofenceKeysPattern())) return;
            String detailKey = keyGenerator.getGeofenceDetailKey(geofenceId);
            redisTemplate.delete(detailKey);
        } catch (Exception e) {
            logger.error("Failed to invalidate geofence cache: {}", geofenceId, e);
        }
    }

    public void invalidateVehicleGeofenceCache(String vehicleId) {
        try {
            if (TransactionalCacheInvalidation.defer(redisTemplate, keyGenerator.getAllGeofenceKeysPattern())) return;
            String allKey = keyGenerator.getVehicleGeofencesKey(vehicleId);
            String activeKey = keyGenerator.getActiveVehicleGeofencesKey(vehicleId);
            List<String> keysToDelete = Arrays.asList(allKey, activeKey);

            redisTemplate.delete(keysToDelete);
        } catch (Exception e) {
            logger.error("Failed to invalidate vehicle geofence cache: {}", vehicleId, e);
        }
    }

    public void invalidateVehicleGeofenceCaches(Set<String> vehicleIds) {
        try {
            for (String vehicleId : vehicleIds) {
                invalidateVehicleGeofenceCache(vehicleId);
            }
        } catch (Exception e) {
            logger.error("Failed to invalidate vehicle geofence caches", e);
        }
    }

    public void clearAll() {
        try {
            if (TransactionalCacheInvalidation.defer(redisTemplate, keyGenerator.getAllGeofenceKeysPattern())) return;
            List<String> keys = RedisKeyScanner.scanKeys(redisTemplate, keyGenerator.getAllGeofenceKeysPattern());
            if (!keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception e) {
            logger.error("Failed to clear geofence cache", e);
        }
    }
}
