package com.surf.tracker.alerting.infrastructure.cache;

import com.surf.tracker.alerting.domain.entity.AlertRule;
import com.surf.tracker.alerting.infrastructure.repository.AlertRuleRepository;
import com.surf.tracker.shared.redis.RedisKeyScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.connection.DataType;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class VehicleRuleCacheService {

    private static final Logger logger = LoggerFactory.getLogger(VehicleRuleCacheService.class);

    private final RedisTemplate<String, Object> redisTemplate;
    private final AlertRuleRepository ruleRepository;
    private final RedisKeyGenerator keyGenerator;

    public VehicleRuleCacheService(
            RedisTemplate<String, Object> redisTemplate,
            AlertRuleRepository ruleRepository,
            RedisKeyGenerator keyGenerator) {
        this.redisTemplate = redisTemplate;
        this.ruleRepository = ruleRepository;
        this.keyGenerator = keyGenerator;
    }

    /**
     * Get active rules for a specific vehicle
     */
    public List<AlertRule> getActiveRulesForVehicle(String vehicleId, Long organizationId) {
        try {
            String cacheKey = keyGenerator.getVehicleRulesKey(vehicleId, organizationId);

            // Empty markers are STRING keys; positive entries are LIST keys.
            if (DataType.STRING.equals(redisTemplate.type(cacheKey))
                    && "EMPTY".equals(redisTemplate.opsForValue().get(cacheKey))) {
                return Collections.emptyList();
            }

            // Check cache first
            List<Object> cachedRules = redisTemplate.opsForList().range(cacheKey, 0, -1);

            if (cachedRules != null && !cachedRules.isEmpty()
                    && cachedRules.stream().allMatch(obj -> obj instanceof AlertRule rule
                        && organizationId.equals(rule.getOrganizationId()))) {
                return cachedRules.stream()
                        .map(obj -> (AlertRule) obj)
                        .collect(Collectors.toList());
            }

            // Cache miss - load from DB and cache
            return loadAndCacheRulesForVehicle(vehicleId, organizationId);

        } catch (Exception e) {
            logger.error("Failed to get rules for vehicle {}", vehicleId, e);
            return Collections.emptyList();
        }
    }

    /**
     * Load and cache rules for vehicle
     */
    private List<AlertRule> loadAndCacheRulesForVehicle(String vehicleId, Long organizationId) {
        try {
            List<AlertRule> rules = ruleRepository.findActiveRulesForVehicleAndOrganizationId(vehicleId, organizationId);

            if (rules.isEmpty()) {
                // Cache empty result with short TTL
                cacheEmptyResult(vehicleId, organizationId);
                return Collections.emptyList();
            }

            // Sort by priority descending and cache
            List<AlertRule> sortedRules = rules.stream()
                    .sorted(Comparator.comparingInt(AlertRule::getPriority).reversed())
                    .collect(Collectors.toList());

            cacheRules(vehicleId, sortedRules, organizationId);

            logger.debug("Loaded {} rules for vehicle {}", sortedRules.size(), vehicleId);

            return sortedRules;

        } catch (Exception e) {
            logger.error("Failed to load rules for vehicle {}", vehicleId, e);
            return Collections.emptyList();
        }
    }

    /**
     * Cache rules for vehicle
     */
    private void cacheRules(String vehicleId, List<AlertRule> rules, Long organizationId) {
        try {
            if (TransactionalCacheInvalidation.defer(redisTemplate, keyGenerator.getAllRuleKeysPattern(organizationId))) return;
            String cacheKey = keyGenerator.getVehicleRulesKey(vehicleId, organizationId);

            // Delete existing
            redisTemplate.delete(cacheKey);

            if (!rules.isEmpty()) {
                // Push all rules to list
                redisTemplate.opsForList().rightPushAll(cacheKey, rules.toArray());

                // Set TTL
                redisTemplate.expire(cacheKey, RedisKeyGenerator.VEHICLE_RULE_CACHE_TTL, TimeUnit.SECONDS);
            }

            logger.debug("Cached {} rules for vehicle {}", rules.size(), vehicleId);

        } catch (Exception e) {
            logger.error("Failed to cache rules for vehicle {}", vehicleId, e);
        }
    }

    /**
     * Cache empty result (no rules for this vehicle)
     */
    private void cacheEmptyResult(String vehicleId, Long organizationId) {
        try {
            if (TransactionalCacheInvalidation.defer(redisTemplate, keyGenerator.getAllRuleKeysPattern(organizationId))) return;
            String cacheKey = keyGenerator.getVehicleRulesKey(vehicleId, organizationId);

            // Store special marker for empty result with short TTL
            redisTemplate.opsForValue().set(cacheKey, "EMPTY", 5, TimeUnit.MINUTES);

            logger.debug("Cached empty result for vehicle {}", vehicleId);

        } catch (Exception e) {
            logger.error("Failed to cache empty result for vehicle {}", vehicleId, e);
        }
    }

    /**
     * Check if vehicle has any active rules (quick cache check)
     */
    public boolean hasActiveRules(String vehicleId, Long organizationId) {
        return !getActiveRulesForVehicle(vehicleId, organizationId).isEmpty();
    }

    /**
     * Get all vehicles that have active rules (for bulk operations)
     */
    public Set<String> getVehiclesWithActiveRules(Long organizationId) {
        try {
            Set<String> vehiclesWithRules = ruleRepository.findVehiclesWithActiveRules(organizationId);
            if (TransactionalCacheInvalidation.defer(redisTemplate, keyGenerator.getAllRuleKeysPattern(organizationId))) {
                return vehiclesWithRules;
            }

            // Cache this set as well for quick reference
            String key = keyGenerator.getVehiclesWithRulesKey(organizationId);
            redisTemplate.delete(key);

            if (!vehiclesWithRules.isEmpty()) {
                redisTemplate.opsForSet().add(key, vehiclesWithRules.toArray());
                redisTemplate.expire(key, keyGenerator.INDEX_CACHE_TTL, TimeUnit.SECONDS);
            }

            return vehiclesWithRules;

        } catch (Exception e) {
            logger.error("Failed to get vehicles with active rules", e);
            return Collections.emptySet();
        }
    }

    /**
     * Quick check if vehicle has rules (using cached index)
     */
    public boolean hasRulesCached(String vehicleId) {
        try {
            String key = keyGenerator.getVehiclesWithRulesKey();
            Boolean hasRule = redisTemplate.opsForSet().isMember(key, vehicleId);
            // Index absence is a cache miss, not evidence that a vehicle has no rules.
            return Boolean.TRUE.equals(hasRule) || hasActiveRules(vehicleId,
                com.surf.tracker.shared.security.TenantContext.getCurrentOrgId());
        } catch (Exception e) {
            logger.error("Failed to check cached rules index for vehicle {}", vehicleId, e);
            return false;
        }
    }

    /**
     * Invalidate cache for specific vehicle
     */
    public void invalidateVehicleRules(String vehicleId) {
        try {
            if (TransactionalCacheInvalidation.defer(redisTemplate, keyGenerator.getAllRuleKeysPattern())) return;
            String cacheKey = keyGenerator.getVehicleRulesKey(vehicleId);
            redisTemplate.delete(cacheKey);

            // Also remove from vehicles-with-rules index
            String indexKey = keyGenerator.getVehiclesWithRulesKey();
            redisTemplate.opsForSet().remove(indexKey, vehicleId);

            logger.debug("Invalidated cache for vehicle {}", vehicleId);
        } catch (Exception e) {
            logger.error("Failed to invalidate cache for vehicle {}", vehicleId, e);
        }
    }

    /**
     * Batch invalidate - when rules change
     */
    public void invalidateAllVehicleRules() {
        try {
            if (TransactionalCacheInvalidation.defer(redisTemplate, keyGenerator.getAllRuleKeysPattern())) return;
            List<String> keys = RedisKeyScanner.scanKeys(redisTemplate, keyGenerator.getVehicleRulesKey("*"));
            if (!keys.isEmpty()) {
                redisTemplate.delete(keys);
            }

            // Clear vehicles index
            String indexKey = keyGenerator.getVehiclesWithRulesKey();
            redisTemplate.delete(indexKey);

            logger.info("Invalidated all vehicle rule caches");
        } catch (Exception e) {
            logger.error("Failed to invalidate all vehicle caches", e);
        }
    }

    /**
     * Pre-warm cache for frequently accessed vehicles
     */
    public void prewarmCacheForHighPriorityVehicles(Long organizationId) {
        try {
            Set<String> highPriorityVehicles = getHighPriorityVehicles();

            for (String vehicleId : highPriorityVehicles) {
                loadAndCacheRulesForVehicle(vehicleId, organizationId);
            }

            logger.info("Pre-warmed cache for {} vehicles", highPriorityVehicles.size());

        } catch (Exception e) {
            logger.error("Failed to pre-warm cache", e);
        }
    }

    private Set<String> getHighPriorityVehicles() {
        // Business logic: VIP vehicles, recently active, etc.
        // Could be from configuration, analytics, or recent activity
        return new HashSet<>(); // Implement based on your needs
    }
}
