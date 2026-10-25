package com.surf.tracker.alerting.application.dedup;

import com.surf.tracker.alerting.domain.AlertDetectedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class AlertDeduplicator {

    private static final Logger logger = LoggerFactory.getLogger(AlertDeduplicator.class);
    private static final String KEY_FORMAT = "alert:dedup:%s:%s";

    private final RedisTemplate<String, Object> redisTemplate;
    private final ValueOperations<String, Object> valueOps;

    public AlertDeduplicator(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.valueOps = redisTemplate.opsForValue();
    }

    public boolean tryAcquire(AlertDetectedEvent event) {
        String key = buildKey(event);
        try {
            Boolean acquired = valueOps.setIfAbsent(key, System.currentTimeMillis(),
                    Duration.ofMinutes(event.getCooldownMinutes()));
            if (acquired == null) {
                return true;
            }
            if (!acquired) {
                logger.debug("Suppressing duplicate alert: rule={} vehicle={} (cooldown active)",
                        event.getRuleKey(), event.getVehicleId());
            }
            return acquired;
        } catch (Exception e) {
            logger.warn("Failed to check dedup state for rule={} vehicle={}: {}",
                    event.getRuleKey(), event.getVehicleId(), e.getMessage());
            return true;
        }
    }

    public void release(AlertDetectedEvent event) {
        try {
            String key = buildKey(event);
            redisTemplate.delete(key);
        } catch (Exception e) {
            logger.warn("Failed to release dedup state for rule={} vehicle={}: {}",
                    event.getRuleKey(), event.getVehicleId(), e.getMessage());
        }
    }

    private String buildKey(AlertDetectedEvent event) {
        return String.format(KEY_FORMAT, event.getRuleKey(), event.getVehicleId());
    }
}
