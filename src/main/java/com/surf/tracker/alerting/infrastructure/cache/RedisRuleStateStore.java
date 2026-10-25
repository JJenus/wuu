package com.surf.tracker.alerting.infrastructure.cache;

import com.surf.tracker.alerting.domain.RuleStateStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Component
public class RedisRuleStateStore implements RuleStateStore {

    private static final Logger logger = LoggerFactory.getLogger(RedisRuleStateStore.class);
    private static final String GEOFENCE_KEY = "rule:state:geofence:%s";
    private static final String GEOFENCE_DWELL_KEY = "rule:state:geofence:dwell:%s";
    private static final String IDLE_KEY = "rule:state:idle:%s";

    private final RedisTemplate<String, Object> redisTemplate;
    private final HashOperations<String, String, Object> hashOps;

    public RedisRuleStateStore(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.hashOps = redisTemplate.opsForHash();
    }

    @Override
    public Optional<Boolean> getGeofenceWasInside(String ruleKey, String vehicleId) {
        try {
            String key = String.format(GEOFENCE_KEY, ruleKey);
            Object value = hashOps.get(key, vehicleId);
            if (value instanceof Boolean b) {
                return Optional.of(b);
            }
            return Optional.empty();
        } catch (Exception e) {
            logger.warn("Failed to load geofence state for rule={} vehicle={}: {}",
                    ruleKey, vehicleId, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void setGeofenceWasInside(String ruleKey, String vehicleId, boolean wasInside) {
        try {
            String key = String.format(GEOFENCE_KEY, ruleKey);
            hashOps.put(key, vehicleId, wasInside);
        } catch (Exception e) {
            logger.warn("Failed to save geofence state for rule={} vehicle={}: {}",
                    ruleKey, vehicleId, e.getMessage());
        }
    }

    @Override
    public Optional<Instant> getGeofenceEntryTime(String ruleKey, String vehicleId) {
        try {
            String key = String.format(GEOFENCE_DWELL_KEY, ruleKey);
            Object value = hashOps.get(key, vehicleId);
            if (value instanceof Number n) {
                return Optional.of(Instant.ofEpochMilli(n.longValue()));
            }
            if (value instanceof String s) {
                return Optional.of(Instant.parse(s));
            }
            return Optional.empty();
        } catch (Exception e) {
            logger.warn("Failed to load geofence entry time for rule={} vehicle={}: {}",
                    ruleKey, vehicleId, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void setGeofenceEntryTime(String ruleKey, String vehicleId, Instant entryTime) {
        try {
            String key = String.format(GEOFENCE_DWELL_KEY, ruleKey);
            hashOps.put(key, vehicleId, entryTime.toEpochMilli());
        } catch (Exception e) {
            logger.warn("Failed to save geofence entry time for rule={} vehicle={}: {}",
                    ruleKey, vehicleId, e.getMessage());
        }
    }

    @Override
    public void clearGeofenceEntryTime(String ruleKey, String vehicleId) {
        try {
            String key = String.format(GEOFENCE_DWELL_KEY, ruleKey);
            hashOps.delete(key, vehicleId);
        } catch (Exception e) {
            logger.warn("Failed to clear geofence entry time for rule={} vehicle={}: {}",
                    ruleKey, vehicleId, e.getMessage());
        }
    }

    @Override
    public Optional<Instant> getLastMovementTime(String ruleKey, String vehicleId) {
        try {
            String key = String.format(IDLE_KEY, ruleKey);
            Object value = hashOps.get(key, vehicleId);
            if (value instanceof Number n) {
                return Optional.of(Instant.ofEpochMilli(n.longValue()));
            }
            if (value instanceof String s) {
                return Optional.of(Instant.parse(s));
            }
            return Optional.empty();
        } catch (Exception e) {
            logger.warn("Failed to load last movement time for rule={} vehicle={}: {}",
                    ruleKey, vehicleId, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void setLastMovementTime(String ruleKey, String vehicleId, Instant time) {
        try {
            String key = String.format(IDLE_KEY, ruleKey);
            hashOps.put(key, vehicleId, time.toEpochMilli());
        } catch (Exception e) {
            logger.warn("Failed to save last movement time for rule={} vehicle={}: {}",
                    ruleKey, vehicleId, e.getMessage());
        }
    }

    @Override
    public Map<String, Instant> getAllLastMovementTimes(String ruleKey) {
        Map<String, Instant> result = new HashMap<>();
        try {
            String key = String.format(IDLE_KEY, ruleKey);
            Map<String, Object> entries = hashOps.entries(key);
            entries.forEach((k, v) -> {
                if (v instanceof Number n) {
                    result.put(k, Instant.ofEpochMilli(n.longValue()));
                } else if (v instanceof String s) {
                    result.put(k, Instant.parse(s));
                }
            });
        } catch (Exception e) {
            logger.warn("Failed to load all last movement times for rule={}: {}", ruleKey, e.getMessage());
        }
        return result;
    }
}
