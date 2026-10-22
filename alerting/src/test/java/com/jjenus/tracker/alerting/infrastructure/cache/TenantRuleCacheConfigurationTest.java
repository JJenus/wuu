package com.jjenus.tracker.alerting.infrastructure.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjenus.tracker.alerting.infrastructure.config.CacheConfig;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.cache.transaction.TransactionAwareCacheDecorator;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class TenantRuleCacheConfigurationTest {
    @Test
    void tenantRegions_productionConfiguration_preserveLogicalCachePolicies() {
        // given: constructing cache metadata does not connect to Redis.
        RedisCacheManager manager = (RedisCacheManager) new CacheConfig()
            .cacheManager(mock(RedisConnectionFactory.class), new ObjectMapper());
        manager.afterPropertiesSet();

        // when / then
        RedisCache rules = target(manager, "alertRules:v2:org:1");
        RedisCache vehicles = target(manager, "vehicleRules:v2:org:1");
        RedisCache pages = target(manager, "alertRulesPaged:v2:org:1");
        assertThat(rules.getCacheConfiguration().getTtl()).isEqualTo(Duration.ofHours(12));
        assertThat(vehicles.getCacheConfiguration().getTtl()).isEqualTo(Duration.ofHours(6));
        assertThat(pages.getCacheConfiguration().getTtl()).isEqualTo(Duration.ofHours(1));
        assertThat(rules.getCacheConfiguration().getAllowCacheNullValues()).isFalse();
        assertThat(rules.getCacheConfiguration().getKeyPrefixFor(rules.getName()))
            .isEqualTo("cache:alertRules:v2:org:1::");
        RedisCache geofences = target(manager, "geofencesPaged:v2:org:2");
        RedisCache alerts = target(manager, "alerts:v2:org:2");
        RedisCache stats = target(manager, "alertStats:v2:org:2");
        assertThat(geofences.getCacheConfiguration().getTtl()).isEqualTo(Duration.ofMinutes(5));
        assertThat(alerts.getCacheConfiguration().getTtl()).isEqualTo(Duration.ofMinutes(20));
        assertThat(stats.getCacheConfiguration().getTtl()).isEqualTo(Duration.ofMinutes(5));
    }

    private RedisCache target(RedisCacheManager manager, String name) {
        assertThat(manager.getCache(name)).isInstanceOf(TransactionAwareCacheDecorator.class);
        return (RedisCache) ((TransactionAwareCacheDecorator) manager.getCache(name)).getTargetCache();
    }
}
