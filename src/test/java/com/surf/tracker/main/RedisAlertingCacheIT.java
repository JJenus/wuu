package com.surf.tracker.main;

import com.surf.tracker.alerting.api.dto.AlertResponse;
import com.surf.tracker.alerting.api.dto.GeofenceResponse;
import com.surf.tracker.alerting.api.dto.PagedResponse;
import com.surf.tracker.alerting.domain.entity.TrackerAlert;
import com.surf.tracker.alerting.domain.entity.Geofence;
import com.surf.tracker.alerting.domain.entity.AlertRule;
import com.surf.tracker.alerting.infrastructure.cache.AlertCacheService;
import com.surf.tracker.alerting.infrastructure.cache.GeofenceCacheService;
import com.surf.tracker.alerting.infrastructure.cache.AlertRuleCacheService;
import com.surf.tracker.alerting.infrastructure.cache.RedisKeyGenerator;
import com.surf.tracker.alerting.infrastructure.repository.AlertRuleRepository;
import com.surf.tracker.alerting.infrastructure.repository.TrackerAlertRepository;
import com.surf.tracker.alerting.application.service.AlertService;
import com.surf.tracker.alerting.api.dto.UpdateAlertRequest;
import com.surf.tracker.alerting.infrastructure.cache.TenantAlertRuleCacheResolver;
import com.surf.tracker.alerting.infrastructure.cache.AlertCacheKeyGenerator;
import com.surf.tracker.shared.security.TenantContext;
import com.surf.tracker.alerting.infrastructure.config.CacheConfig;
import com.surf.tracker.main.config.AppConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Instant;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

/** Explicit opt-in: use a disposable local Redis, never an application Redis database. */
class RedisAlertingCacheIT {
    private LettuceConnectionFactory connectionFactory;
    private RedisCacheManager manager;
    private RedisTemplate<String, Object> redis;
    private final List<Cache> usedCaches = new ArrayList<>();
    private final List<String> manualKeys = new ArrayList<>();

    @BeforeEach
    void setUp() {
        String port = System.getProperty("tracker.test.redis.port");
        if (port == null) throw new IllegalStateException("Set tracker.test.redis.port to a disposable Redis port");
        connectionFactory = new LettuceConnectionFactory("127.0.0.1", Integer.parseInt(port));
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        AppConfig app = new AppConfig();
        manager = (RedisCacheManager) new CacheConfig().cacheManager(connectionFactory, app.objectMapper());
        manager.afterPropertiesSet();
        redis = app.redisTemplate(connectionFactory, app.objectMapper());
        redis.afterPropertiesSet();
    }

    @AfterEach
    void tearDown() {
        try {
            for (Cache cache : usedCaches) cache.clear();
            if (redis != null && !manualKeys.isEmpty()) redis.delete(manualKeys);
        } finally {
            TenantContext.clear();
            if (connectionFactory != null) connectionFactory.destroy();
        }
    }

    @Test
    void alertDetail_realRedis_roundTripsResponseAndInstant() {
        // given
        Cache cache = cache("alerts", 91001);
        AlertResponse response = alert("First tenant");

        // when
        cache.put("detail", response);
        AlertResponse restored = cache.get("detail", AlertResponse.class);

        // then
        assertThat(restored).isNotSameAs(response);
        assertThat(restored.getMessage()).isEqualTo("First tenant");
        assertThat(restored.getTriggeredAt()).isEqualTo(Instant.EPOCH);
    }

    @Test
    void deduplication_expiredHolderCannotReleaseNewerOwnersLease() {
        // given: a first publisher stalls until its cooldown lease has expired.
        var dedup = new com.surf.tracker.alerting.application.dedup.AlertDeduplicator(redis);
        var clock = Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"), ZoneOffset.UTC);
        var first = new com.surf.tracker.alerting.domain.AlertDetectedEvent(clock, "lease-rule",
            com.surf.tracker.alerting.domain.enums.AlertType.OVERSPEED, "lease-vehicle", "first",
            com.surf.tracker.alerting.domain.enums.AlertSeverity.WARNING, null);
        var newer = new com.surf.tracker.alerting.domain.AlertDetectedEvent(clock, "lease-rule",
            com.surf.tracker.alerting.domain.enums.AlertType.OVERSPEED, "lease-vehicle", "newer",
            com.surf.tracker.alerting.domain.enums.AlertSeverity.WARNING, null);
        var third = new com.surf.tracker.alerting.domain.AlertDetectedEvent(clock, "lease-rule",
            com.surf.tracker.alerting.domain.enums.AlertType.OVERSPEED, "lease-vehicle", "third",
            com.surf.tracker.alerting.domain.enums.AlertSeverity.WARNING, null);
        String key = "alert:dedup:lease-rule:lease-vehicle";
        manualKeys.add(key);
        redis.delete(key);
        var firstLease = dedup.acquire(first);
        assertThat(firstLease).isNotNull();
        redis.delete(key); // Deterministically model the old lease expiring, without sleeping five minutes.
        var newerLease = dedup.acquire(newer);
        assertThat(newerLease).isNotNull();
        // when: the old publisher finally fails and tries to release its stale lease.
        dedup.release(firstLease);
        // then: only the current holder can release the replacement lease.
        assertThat(dedup.tryAcquire(third)).isFalse();
        dedup.release(newerLease);
        assertThat(dedup.tryAcquire(third)).isTrue();
    }

    @Test
    void deduplication_sameEventReacquired_usesDistinctAttemptOwnership() {
        // given: replay-safe event identity is stable, but acquisition ownership must not be.
        var dedup = new com.surf.tracker.alerting.application.dedup.AlertDeduplicator(redis);
        var event = new com.surf.tracker.alerting.domain.AlertDetectedEvent(
            Clock.fixed(Instant.EPOCH, ZoneOffset.UTC), "same-event-lease",
            com.surf.tracker.alerting.domain.enums.AlertType.OVERSPEED, "same-event-vehicle", "fixture",
            com.surf.tracker.alerting.domain.enums.AlertSeverity.WARNING, null);
        String key = "alert:dedup:same-event-lease:same-event-vehicle";
        manualKeys.add(key);
        redis.delete(key);
        var oldLease = dedup.acquire(event);
        assertThat(oldLease).isNotNull();
        redis.delete(key);
        var currentLease = dedup.acquire(event);
        assertThat(currentLease).isNotNull().isNotEqualTo(oldLease);
        // when / then
        dedup.release(oldLease);
        assertThat(dedup.acquire(event)).isNull();
        dedup.release(currentLease);
        assertThat(dedup.acquire(event)).isNotNull();
    }

    @Test
    void alertList_realRedis_roundTripsTypedElements() {
        // given
        Cache cache = cache("alerts", 91001);
        List<AlertResponse> responses = new ArrayList<>(List.of(alert("First tenant")));

        // when
        cache.put("active", responses);
        List<?> restored = cache.get("active", List.class);

        // then
        assertThat(restored).hasSize(1);
        assertThat(restored.getFirst()).isInstanceOf(AlertResponse.class);
    }

    @Test
    void geofencePage_realRedis_roundTripsPageAndTypedContent() {
        // given
        Cache cache = cache("geofencesPaged", 91001);
        GeofenceResponse response = new GeofenceResponse();
        response.setName("First tenant");
        response.setCreatedAt(Instant.EPOCH);
        PagedResponse<GeofenceResponse> page = new PagedResponse<>(new PageImpl<>(List.of(response)));

        // when
        cache.put("page", page);
        PagedResponse<?> restored = cache.get("page", PagedResponse.class);

        // then
        assertThat(restored.getContent()).hasSize(1);
        assertThat(restored.getContent().getFirst()).isInstanceOf(GeofenceResponse.class);
        assertThat(restored.getTotalElements()).isEqualTo(1);
    }

    @Test
    void statistics_realRedis_roundTripsImmutableMap() {
        // given
        Cache cache = cache("alertStats", 91001);

        // when
        cache.put("counts", Map.of("WARNING", 2L));

        // then
        assertThat(cache.get("counts", Map.class)).containsEntry("WARNING", 2L);
    }

    @Test
    void manualAlerts_realRedis_roundTripsDetailsAndVehicleLists() {
        // given
        TenantContext.setCurrentOrgId(91001L);
        RedisKeyGenerator keys = new RedisKeyGenerator();
        manualKeys.add(keys.getAlertDetailKey(1L));
        manualKeys.add(keys.getActiveVehicleAlertsKey("redis-it-vehicle"));
        AlertCacheService service = new AlertCacheService(redis, keys);
        TrackerAlert alert = new TrackerAlert();
        alert.setAlertId(1L); alert.setOrganizationId(91001L); alert.setVehicle("redis-it-vehicle");
        alert.setMetadata(Map.of("speedKmh", 100L));

        // when
        service.cacheAlert(alert);
        service.cacheActiveVehicleAlerts("redis-it-vehicle", List.of(alert));

        // then
        assertThat(service.getAlertById(1L)).isPresent();
        assertThat(service.getActiveVehicleAlerts("redis-it-vehicle")).hasSize(1);
        assertThat(service.getAlertById(1L).orElseThrow().getMetadata()).containsEntry("speedKmh", 100L);
        TenantContext.setCurrentOrgId(91002L);
        assertThat(service.getAlertById(1L)).isEmpty();
    }

    @Test
    void manualGeofences_realRedis_roundTripsDetailsAndVehicleLists() {
        // given
        TenantContext.setCurrentOrgId(91001L);
        RedisKeyGenerator keys = new RedisKeyGenerator();
        manualKeys.add(keys.getGeofenceDetailKey(1L));
        manualKeys.add(keys.getVehicleGeofencesKey("redis-it-vehicle"));
        GeofenceCacheService service = new GeofenceCacheService(redis, keys);
        Geofence geofence = new Geofence();
        geofence.setGeofenceId(1L); geofence.setOrganizationId(91001L);
        geofence.setVehicleIds(java.util.Set.of("redis-it-vehicle"));
        geofence.setCreatedAt(Instant.EPOCH); geofence.setUpdatedAt(Instant.EPOCH);

        // when
        service.cacheGeofence(geofence);
        service.cacheVehicleGeofences("redis-it-vehicle", List.of(geofence));

        // then
        assertThat(service.getGeofenceById(1L)).isPresent();
        assertThat(service.getVehicleGeofences("redis-it-vehicle")).hasSize(1);
    }

    @Test
    void activeRulePipeline_realRedis_usesCachedEntitiesWithoutDatabaseFallback() {
        // given
        TenantContext.setCurrentOrgId(91001L);
        RedisKeyGenerator keys = new RedisKeyGenerator();
        manualKeys.add(keys.getRuleDetailKey("redis-it-rule"));
        manualKeys.add(keys.getAllActiveRulesKey());
        AlertRuleRepository repository = mock(AlertRuleRepository.class);
        AlertRuleCacheService service = new AlertRuleCacheService(redis, repository, keys, new AppConfig().objectMapper());
        AlertRule rule = new AlertRule();
        rule.setRuleKey("redis-it-rule"); rule.setOrganizationId(91001L);
        rule.setVehicleIds(java.util.Set.of("redis-it-vehicle"));
        service.cacheRule(rule);

        // when
        List<AlertRule> restored = service.getAllActiveRules(91001L);

        // then
        assertThat(restored).hasSize(1);
        assertThat(restored.getFirst().getOrganizationId()).isEqualTo(91001L);
        verifyNoInteractions(repository);
    }

    @Test
    void alertProxy_realRedis_reusesTypedResultsAndEvictsOnlyWriter() {
        // given
        cache("alerts", 91001); cache("alerts", 91002);
        cache("alertsPaged", 91001); cache("alertStats", 91001);
        TrackerAlertRepository repository = mock(TrackerAlertRepository.class);
        TrackerAlert first = new TrackerAlert(); first.setAlertId(1L); first.setOrganizationId(91001L);
        first.setMessage("First tenant");
        TrackerAlert second = new TrackerAlert(); second.setAlertId(2L); second.setOrganizationId(91002L);
        second.setMessage("Second tenant");
        when(repository.findActiveVehicleAlerts("same-vehicle", 91001L)).thenReturn(List.of(first));
        when(repository.findActiveVehicleAlerts("same-vehicle", 91002L)).thenReturn(List.of(second));
        when(repository.findByIdAndOrganizationId(1L, 91001L)).thenReturn(java.util.Optional.of(first));
        when(repository.save(first)).thenReturn(first);
        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(ProxyConfiguration.class, TenantAlertRuleCacheResolver.class, AlertCacheKeyGenerator.class,
                AlertService.class, AlertService.AlertQueryService.class);
            context.registerBean(CacheManager.class, () -> manager);
            context.registerBean(com.fasterxml.jackson.databind.ObjectMapper.class, () -> new AppConfig().objectMapper());
            context.registerBean(Clock.class, () -> Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
            context.registerBean(TrackerAlertRepository.class, () -> repository);
            context.refresh();
            AlertService service = context.getBean(AlertService.class);
            TenantContext.setCurrentOrgId(91001L);
            service.getActiveAlerts("same-vehicle");
            assertThat(service.getActiveAlerts("same-vehicle")).extracting(AlertResponse::getMessage)
                .containsExactly("First tenant");
            TenantContext.setCurrentOrgId(91002L);
            service.getActiveAlerts("same-vehicle");

            // when
            TenantContext.setCurrentOrgId(91001L);
            UpdateAlertRequest update = new UpdateAlertRequest(); update.setMessage("Changed");
            service.updateAlert(1L, update);

            // then
            assertThat(service.getActiveAlerts("same-vehicle")).extracting(AlertResponse::getMessage)
                .containsExactly("Changed");
            TenantContext.setCurrentOrgId(91002L);
            assertThat(service.getActiveAlerts("same-vehicle")).extracting(AlertResponse::getMessage)
                .containsExactly("Second tenant");
            verify(repository, times(2)).findActiveVehicleAlerts("same-vehicle", 91001L);
            verify(repository, times(1)).findActiveVehicleAlerts("same-vehicle", 91002L);
        }
    }

    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
    @EnableCaching
    static class ProxyConfiguration {}

    @Test
    void clearTenant_realRedis_preservesOtherTenantAndTtl() {
        // given
        Cache first = cache("alerts", 91001);
        Cache second = cache("alerts", 91002);
        first.put("detail", alert("First tenant"));
        second.put("detail", alert("Second tenant"));

        // when
        first.clear();

        // then
        assertThat(first.get("detail")).isNull();
        assertThat(second.get("detail", AlertResponse.class).getMessage()).isEqualTo("Second tenant");
        assertThat(redis.getExpire("cache:alerts:v2:org:91002::detail")).isBetween(1L, 1200L);
    }

    private Cache cache(String name, long organization) {
        Cache cache = manager.getCache(name + ":v2:org:" + organization);
        usedCaches.add(cache);
        cache.clear();
        return cache;
    }

    private AlertResponse alert(String message) {
        AlertResponse response = new AlertResponse();
        response.setAlertId(1L);
        response.setMessage(message);
        response.setTriggeredAt(Instant.EPOCH);
        return response;
    }
}
