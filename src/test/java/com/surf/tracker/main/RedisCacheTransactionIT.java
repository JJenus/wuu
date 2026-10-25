package com.surf.tracker.main;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.surf.tracker.alerting.api.dto.UpdateAlertRequest;
import com.surf.tracker.alerting.application.service.AlertService;
import com.surf.tracker.alerting.application.service.GeofenceService;
import com.surf.tracker.alerting.domain.entity.AlertRule;
import com.surf.tracker.alerting.domain.entity.Geofence;
import com.surf.tracker.alerting.domain.entity.TrackerAlert;
import com.surf.tracker.alerting.domain.enums.AlertType;
import com.surf.tracker.alerting.infrastructure.cache.*;
import com.surf.tracker.alerting.infrastructure.config.CacheConfig;
import com.surf.tracker.alerting.infrastructure.repository.AlertRuleRepository;
import com.surf.tracker.alerting.infrastructure.repository.GeofenceRepository;
import com.surf.tracker.alerting.infrastructure.repository.TrackerAlertRepository;
import com.surf.tracker.main.config.AppConfig;
import com.surf.tracker.shared.redis.RedisKeyScanner;
import com.surf.tracker.shared.security.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real H2/JPA transactions + Redis; repository test adapters delegate to the real EntityManager. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RedisCacheTransactionIT {
    private AnnotationConfigApplicationContext context;
    private LocalContainerEntityManagerFactoryBean factory;
    private EntityManager em;
    private LettuceConnectionFactory redisFactory;
    private RedisTemplate<String, Object> redis;
    private CacheManager caches;
    private TransactionTemplate transactions;
    private AlertService alerts;
    private GeofenceService geofences;
    private GeofenceCacheService geofenceCache;
    private AlertRuleCacheService ruleCache;
    private long alertId;
    private long geofenceId;
    private long ruleId;

    @BeforeAll
    void start() {
        String port = System.getProperty("tracker.test.redis.port");
        if (port == null) throw new IllegalStateException("Set tracker.test.redis.port to a disposable Redis port");
        JdbcDataSource database = new JdbcDataSource();
        database.setURL("jdbc:h2:mem:cache_transactions;DB_CLOSE_DELAY=-1;MODE=PostgreSQL");
        factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(database);
        factory.setPackagesToScan("com.surf.tracker.alerting.domain.entity");
        factory.setPersistenceProviderClass(org.hibernate.jpa.HibernatePersistenceProvider.class);
        factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "create-drop"));
        factory.afterPropertiesSet();
        EntityManagerFactory emf = factory.getObject();
        em = SharedEntityManagerCreator.createSharedEntityManager(emf);
        JpaTransactionManager transactionManager = new JpaTransactionManager(emf);
        transactions = new TransactionTemplate(transactionManager);
        redisFactory = new LettuceConnectionFactory("127.0.0.1", Integer.parseInt(port));
        redisFactory.afterPropertiesSet(); redisFactory.start();

        // Keep the test about transaction/cache ordering, not unrelated Spring Data query validation.
        TrackerAlertRepository alertRepository = mock(TrackerAlertRepository.class);
        when(alertRepository.findByIdAndOrganizationId(anyLong(), anyLong())).thenAnswer(invocation -> {
            TrackerAlert value = em.find(TrackerAlert.class, invocation.getArgument(0));
            return Optional.ofNullable(value).filter(entity -> entity.getOrganizationId().equals(invocation.getArgument(1)));
        });
        when(alertRepository.save(any(TrackerAlert.class))).thenAnswer(invocation -> em.merge(invocation.getArgument(0)));
        GeofenceRepository geofenceRepository = mock(GeofenceRepository.class);
        when(geofenceRepository.findByIdAndOrganizationId(anyLong(), anyLong())).thenAnswer(invocation -> {
            Geofence value = em.find(Geofence.class, invocation.getArgument(0));
            return Optional.ofNullable(value).filter(entity -> entity.getOrganizationId().equals(invocation.getArgument(1)));
        });
        when(geofenceRepository.save(any(Geofence.class))).thenAnswer(invocation -> em.merge(invocation.getArgument(0)));
        AppConfig app = new AppConfig();
        redis = app.redisTemplate(redisFactory, app.objectMapper()); redis.afterPropertiesSet();
        context = new AnnotationConfigApplicationContext();
        context.register(TransactionConfiguration.class, CacheConfig.class, TenantAlertRuleCacheResolver.class,
            AlertCacheKeyGenerator.class, AlertService.class, AlertService.AlertQueryService.class,
            GeofenceService.class, GeofenceCacheService.class, AlertRuleCacheService.class, RedisKeyGenerator.class);
        context.registerBean("transactionManager", JpaTransactionManager.class, () -> transactionManager);
        context.registerBean(ObjectMapper.class, app::objectMapper);
        context.registerBean(Clock.class, () -> Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
        context.registerBean(org.springframework.data.redis.connection.RedisConnectionFactory.class, () -> redisFactory);
        context.registerBean(RedisTemplate.class, () -> redis);
        context.registerBean(TrackerAlertRepository.class, () -> alertRepository);
        context.registerBean(GeofenceRepository.class, () -> geofenceRepository);
        context.registerBean(AlertRuleRepository.class, () -> mock(AlertRuleRepository.class));
        context.refresh();
        caches = context.getBean(CacheManager.class);
        alerts = context.getBean(AlertService.class);
        geofences = context.getBean(GeofenceService.class);
        geofenceCache = context.getBean(GeofenceCacheService.class);
        ruleCache = context.getBean(AlertRuleCacheService.class);
    }

    @BeforeEach
    void seed() {
        TenantContext.setCurrentOrgId(92001L);
        cleanRedis();
        transactions.executeWithoutResult(status -> {
            TrackerAlert alert = new TrackerAlert();
            alert.setOrganizationId(92001L); alert.setMessage("Committed"); alert.setAlertType(AlertType.OVERSPEED);
            em.persist(alert); alertId = alert.getAlertId();
            Geofence geofence = new Geofence();
            geofence.setOrganizationId(92001L); geofence.setName("Committed");
            geofence.setVehicleIds(new HashSet<>());
            geofence.setCreatedAt(Instant.EPOCH); geofence.setUpdatedAt(Instant.EPOCH);
            em.persist(geofence); geofenceId = geofence.getGeofenceId();
            AlertRule rule = new AlertRule();
            rule.setRuleKey("tx-rule-" + alertId); rule.setRuleName("Committed"); rule.setOrganizationId(92001L);
            rule.setRuleType(com.surf.tracker.alerting.domain.enums.AlertRuleType.SPEED);
            em.persist(rule); ruleId = rule.getRuleId();
        });
    }

    @AfterEach
    void clean() {
        try { cleanRedis(); } finally { TenantContext.clear(); }
    }

    @AfterAll
    void stop() {
        if (context != null) context.close();
        if (redisFactory != null) redisFactory.destroy();
        if (factory != null) factory.destroy();
    }

    @Test
    void updateAlert_outerCommit_evictsOnlyAfterCommitUsingOriginalTenant() {
        // given
        alerts.getAlertById(alertId);
        caches.getCache("alerts:v2:org:92002").put("sentinel", "Other tenant");

        // when
        transactions.executeWithoutResult(status -> {
            updateAlert();
            assertThat(alertKeys()).hasSize(1); // Other readers still see the committed cache until commit.
            TenantContext.setCurrentOrgId(92002L);
        });

        // then
        assertThat(alertKeys()).isEmpty();
        assertThat(caches.getCache("alerts:v2:org:92002").get("sentinel", String.class)).isEqualTo("Other tenant");
        TenantContext.setCurrentOrgId(92001L);
        assertThat(alerts.getAlertById(alertId).getMessage()).isEqualTo("Uncommitted");
    }

    @Test
    void updateAlert_readerFillsOldValueBeforeCommit_commitEvictsThatFill() throws Exception {
        // given
        try (var reader = java.util.concurrent.Executors.newSingleThreadExecutor()) {
            // when
            transactions.executeWithoutResult(status -> {
                updateAlert();
                try {
                    String observed = reader.submit(() -> {
                        TenantContext.setCurrentOrgId(92001L);
                        try { return alerts.getAlertById(alertId).getMessage(); }
                        finally { TenantContext.clear(); }
                    }).get(10, java.util.concurrent.TimeUnit.SECONDS);
                    assertThat(observed).isEqualTo("Committed");
                } catch (Exception exception) {
                    throw new AssertionError("Concurrent cache fill failed", exception);
                }
            });

            // then
            assertThat(alertKeys()).isEmpty();
            assertThat(alerts.getAlertById(alertId).getMessage()).isEqualTo("Uncommitted");
        }
    }

    @Test
    void updateAlert_outerRollback_preservesCommittedCacheAndDatabase() {
        // given
        alerts.getAlertById(alertId);

        // when
        transactions.executeWithoutResult(status -> { updateAlert(); status.setRollbackOnly(); });

        // then
        assertThat(alertKeys()).hasSize(1);
        assertThat(alerts.getAlertById(alertId).getMessage()).isEqualTo("Committed");
        assertThat(transactions.<String>execute(status -> em.find(TrackerAlert.class, alertId).getMessage())).isEqualTo("Committed");
    }

    @Test
    void readAlert_coldCacheInsideRolledBackWrite_doesNotPublishDirtyResult() {
        // given / when
        transactions.executeWithoutResult(status -> {
            em.find(TrackerAlert.class, alertId).setMessage("Uncommitted");
            assertThat(alerts.getAlertById(alertId).getMessage()).isEqualTo("Uncommitted");
            assertThat(alertKeys()).isEmpty();
            status.setRollbackOnly();
        });

        // then
        assertThat(alerts.getAlertById(alertId).getMessage()).isEqualTo("Committed");
    }

    @Test
    void updateGeofence_outerRollback_doesNotPoisonWarmManualDetail() {
        // given
        geofences.getGeofenceById(geofenceId);

        // when
        transactions.executeWithoutResult(status -> { updateGeofence(); status.setRollbackOnly(); });

        // then
        assertThat(geofenceCache.getGeofenceById(geofenceId).orElseThrow().getName()).isEqualTo("Committed");
        assertThat(transactions.<String>execute(status -> em.find(Geofence.class, geofenceId).getName())).isEqualTo("Committed");
    }

    @Test
    void updateGeofence_outerCommit_invalidatesManualDetailUsingCapturedTenant() {
        // given
        geofences.getGeofenceById(geofenceId);

        // when
        transactions.executeWithoutResult(status -> {
            updateGeofence();
            assertThat(geofenceCache.getGeofenceById(geofenceId).orElseThrow().getName()).isEqualTo("Committed");
            TenantContext.setCurrentOrgId(92002L);
        });

        // then
        TenantContext.setCurrentOrgId(92001L);
        assertThat(geofenceCache.getGeofenceById(geofenceId)).isEmpty();
        assertThat(geofences.getGeofenceById(geofenceId).getName()).isEqualTo("Uncommitted");
    }

    @Test
    void readGeofence_coldCacheInsideRolledBackWrite_doesNotPublishDirtyEntity() {
        // given / when
        transactions.executeWithoutResult(status -> {
            em.find(Geofence.class, geofenceId).setName("Uncommitted");
            assertThat(geofences.getGeofenceById(geofenceId).getName()).isEqualTo("Uncommitted");
            assertThat(geofenceCache.getGeofenceById(geofenceId)).isEmpty();
            status.setRollbackOnly();
        });

        // then
        assertThat(geofences.getGeofenceById(geofenceId).getName()).isEqualTo("Committed");
    }

    @Test
    void cacheRule_outerRollback_doesNotPublishDirtyEntityOrIndex() {
        // given / when
        transactions.executeWithoutResult(status -> {
            AlertRule rule = em.find(AlertRule.class, ruleId);
            rule.setRuleName("Uncommitted");
            ruleCache.cacheRule(rule);
            assertThat(RedisKeyScanner.scanKeys(redis, "alerting:rule:v2:org:92001:*")).isEmpty();
            status.setRollbackOnly();
        });

        // then
        assertThat(transactions.<String>execute(status -> em.find(AlertRule.class, ruleId).getRuleName())).isEqualTo("Committed");
    }

    private void updateAlert() {
        UpdateAlertRequest update = new UpdateAlertRequest(); update.setMessage("Uncommitted");
        alerts.updateAlert(alertId, update);
    }

    private void updateGeofence() {
        Geofence update = new Geofence(); update.setName("Uncommitted");
        geofences.updateGeofence(geofenceId, update);
    }

    private java.util.List<String> alertKeys() {
        return RedisKeyScanner.scanKeys(redis, "cache:alerts:v2:org:92001::*");
    }

    private void cleanRedis() {
        for (String pattern : java.util.List.of("cache:*:v2:org:92001::*", "cache:*:v2:org:92002::*",
                "alerting:*:v2:org:92001:*", "alerting:*:v2:org:92002:*")) {
            var keys = RedisKeyScanner.scanKeys(redis, pattern);
            if (!keys.isEmpty()) redis.delete(keys);
        }
    }

    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class TransactionConfiguration {}
}
