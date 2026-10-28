package com.surf.tracker.alerting.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.surf.tracker.shared.redis.RedisJsonSerializer;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.SimpleCacheErrorHandler;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    @Override
    public CacheErrorHandler errorHandler() {
        // Geofence pages historically used best-effort manual Redis caching.
        // Preserve that policy without changing failure behavior for other caches.
        return new SimpleCacheErrorHandler() {
            private boolean bestEffort(Cache cache, RuntimeException exception) {
                if (!cache.getName().startsWith("geofencesPaged:v2:org:")) return false;
                LoggerFactory.getLogger(CacheConfig.class).warn("Geofence page cache unavailable", exception);
                return true;
            }

            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                if (!bestEffort(cache, exception)) super.handleCacheGetError(exception, cache, key);
            }

            @Override
            public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
                if (!bestEffort(cache, exception)) super.handleCachePutError(exception, cache, key, value);
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                if (!bestEffort(cache, exception)) super.handleCacheEvictError(exception, cache, key);
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                if (!bestEffort(cache, exception)) super.handleCacheClearError(exception, cache);
            }
        };
    }

    @Bean
    public CacheManager cacheManager(
            RedisConnectionFactory connectionFactory,
            ObjectMapper objectMapper) {

        RedisCacheConfiguration defaultConfig =
                RedisCacheConfiguration.defaultCacheConfig()
                        .entryTtl(Duration.ofHours(1))
                        .disableCachingNullValues()
                        .prefixCacheNameWith("cache:")
                        .serializeKeysWith(RedisSerializationContext.SerializationPair
                                .fromSerializer(new StringRedisSerializer()))
                        .serializeValuesWith(RedisSerializationContext.SerializationPair
                                .fromSerializer(RedisJsonSerializer.create(objectMapper)));

        Map<String, RedisCacheConfiguration> cacheConfigs = new HashMap<>();

        cacheConfigs.put("alertRules", defaultConfig.entryTtl(Duration.ofHours(12)));
        cacheConfigs.put("vehicleRules", defaultConfig.entryTtl(Duration.ofHours(6)));
        cacheConfigs.put("geofences", defaultConfig.entryTtl(Duration.ofHours(12)));
        cacheConfigs.put("vehicleGeofences", defaultConfig.entryTtl(Duration.ofHours(6)));

        cacheConfigs.put("alerts", defaultConfig.entryTtl(Duration.ofMinutes(20)));
        cacheConfigs.put("vehicleAlerts", defaultConfig.entryTtl(Duration.ofMinutes(10)));

        cacheConfigs.put("alertStats", defaultConfig.entryTtl(Duration.ofMinutes(5)));
        cacheConfigs.put("geofenceStats", defaultConfig.entryTtl(Duration.ofMinutes(5)));
        cacheConfigs.put("geofencesPaged", defaultConfig.entryTtl(Duration.ofMinutes(5)));

        RedisCacheManager manager = new RedisCacheManager(RedisCacheWriter.nonLockingRedisCacheWriter(connectionFactory),
                defaultConfig, cacheConfigs) {
            @Override
            protected RedisCache getMissingCache(String name) {
                // Tenant regions inherit the TTL/serialization policy of their logical cache.
                String logicalName = name.split(":v2:org:", 2)[0];
                return createRedisCache(name, cacheConfigs.getOrDefault(logicalName, defaultConfig));
            }
        };
        manager.setTransactionAware(true);
        return manager;
    }
}
