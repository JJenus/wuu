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
    private static final org.springframework.data.redis.core.script.DefaultRedisScript<Long> RELEASE_IF_OWNER =
        new org.springframework.data.redis.core.script.DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end", Long.class);

    private final RedisTemplate<String, Object> redisTemplate;
    private final ValueOperations<String, Object> valueOps;

    public AlertDeduplicator(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.valueOps = redisTemplate.opsForValue();
    }

    public boolean tryAcquire(AlertDetectedEvent event) {
        return acquire(event) != null;
    }

    /** A token identifies one acquisition attempt, not the potentially replayed event identity. */
    public record Lease(String key, String token) {}

    public Lease acquire(AlertDetectedEvent event) {
        if (event.getCooldownMinutes() <= 0) return new Lease(null, null);
        String key = buildKey(event);
        String token = java.util.UUID.randomUUID().toString();
        try {
            Boolean acquired = valueOps.setIfAbsent(key, token,
                    Duration.ofMinutes(event.getCooldownMinutes()));
            if (acquired == null) {
                return new Lease(null, null);
            }
            if (!acquired) {
                logger.debug("Suppressing duplicate alert: rule={} vehicle={} (cooldown active)",
                        event.getRuleKey(), event.getVehicleId());
            }
            return acquired ? new Lease(key, token) : null;
        } catch (Exception e) {
            logger.warn("Failed to check dedup state for rule={} vehicle={}: {}",
                    event.getRuleKey(), event.getVehicleId(), e.getMessage());
            return new Lease(null, null);
        }
    }

    public void release(Lease lease) {
        if (lease == null || lease.key() == null || lease.token() == null) return;
        try {
            // Value and script argument use the same configured Redis value serializer.
            redisTemplate.execute(RELEASE_IF_OWNER, java.util.List.of(lease.key()), lease.token());
        } catch (Exception e) {
            logger.warn("Failed to release dedup lease for key {}: {}", lease.key(), e.getMessage());
        }
    }

    private String buildKey(AlertDetectedEvent event) {
        return String.format(KEY_FORMAT, event.getRuleKey(), event.getVehicleId());
    }
}
