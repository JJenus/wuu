package com.surf.tracker.shared.redis;

import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class RedisKeyScanner {
    private static final int BATCH_SIZE = 1000;
    private static final int SCAN_COUNT = 500;

    private RedisKeyScanner() {}

    public static void scanDelete(RedisTemplate<String, Object> redisTemplate, String pattern, Consumer<String> onKey) {
        ScanOptions opts = ScanOptions.scanOptions()
                .match(pattern)
                .count(SCAN_COUNT)
                .build();
        try (Cursor<String> cursor = redisTemplate.scan(opts)) {
            List<String> batch = new ArrayList<>(BATCH_SIZE);
            while (cursor.hasNext()) {
                batch.add(cursor.next());
                if (batch.size() >= BATCH_SIZE) {
                    batch.forEach(onKey);
                    batch.clear();
                }
            }
            if (!batch.isEmpty()) {
                batch.forEach(onKey);
            }
        }
    }

    public static void scanDelete(RedisTemplate<String, Object> redisTemplate, String pattern) {
        scanDelete(redisTemplate, pattern, key -> redisTemplate.delete(key));
    }

    public static List<String> scanKeys(RedisTemplate<String, Object> redisTemplate, String pattern) {
        List<String> keys = new ArrayList<>();
        scanDelete(redisTemplate, pattern, keys::add);
        return keys;
    }
}
