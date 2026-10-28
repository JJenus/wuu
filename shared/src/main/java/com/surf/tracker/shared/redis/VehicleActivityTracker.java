package com.surf.tracker.shared.redis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class VehicleActivityTracker {

    private static final Logger logger = LoggerFactory.getLogger(VehicleActivityTracker.class);
    private static final String KEY_PREFIX = "vehicle:lastseen:";
    private static final int PAGE_SIZE = 1000;

    private final RedisTemplate<String, Object> redisTemplate;
    private final ValueOperations<String, Object> valueOps;

    public VehicleActivityTracker(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.valueOps = redisTemplate.opsForValue();
    }

    public void recordActivity(String vehicleId, Instant timestamp) {
        try {
            String key = KEY_PREFIX + vehicleId;
            valueOps.set(key, timestamp.toEpochMilli());
            redisTemplate.expire(key, Duration.ofHours(24));
        } catch (Exception e) {
            logger.warn("Failed to record activity for vehicle {}: {}", vehicleId, e.getMessage());
        }
    }

    public Optional<Instant> getLastSeen(String vehicleId) {
        try {
            String key = KEY_PREFIX + vehicleId;
            Object value = valueOps.get(key);
            if (value instanceof Number n) {
                return Optional.of(Instant.ofEpochMilli(n.longValue()));
            }
            return Optional.empty();
        } catch (Exception e) {
            logger.warn("Failed to get lastSeen for vehicle {}: {}", vehicleId, e.getMessage());
            return Optional.empty();
        }
    }

    public List<String> getAllActiveVehicleIds() {
        return RedisKeyScanner.scanKeys(redisTemplate, KEY_PREFIX + "*").stream()
                .map(key -> key.substring(KEY_PREFIX.length()))
                .toList();
    }

    public void getAllActiveVehicleIdsBatched(java.util.function.Consumer<List<String>> batchConsumer) {
        List<String> batch = new ArrayList<>(PAGE_SIZE);
        RedisKeyScanner.scanDelete(redisTemplate, KEY_PREFIX + "*", key -> {
            batch.add(key.substring(KEY_PREFIX.length()));
            if (batch.size() >= PAGE_SIZE) {
                batchConsumer.accept(new ArrayList<>(batch));
                batch.clear();
            }
        });
        if (!batch.isEmpty()) {
            batchConsumer.accept(batch);
        }
    }
}
