package com.jjenus.tracker.alerting.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjenus.tracker.alerting.api.dto.SearchRequest;
import com.jjenus.tracker.alerting.api.dto.CreateAlertRuleRequest;
import com.jjenus.tracker.alerting.api.dto.UpdateAlertRuleRequest;
import com.jjenus.tracker.alerting.domain.AlertRuleTestBuilder;
import com.jjenus.tracker.alerting.domain.entity.AlertRule;
import com.jjenus.tracker.alerting.domain.parameters.RuleParametersMapper;
import com.jjenus.tracker.alerting.exception.AlertException;
import com.jjenus.tracker.alerting.infrastructure.cache.AlertRuleCacheService;
import com.jjenus.tracker.alerting.infrastructure.cache.RedisKeyGenerator;
import com.jjenus.tracker.alerting.infrastructure.cache.VehicleRuleCacheService;
import com.jjenus.tracker.alerting.infrastructure.repository.AlertRuleRepository;
import com.jjenus.tracker.shared.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.support.AopUtils;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AlertRuleCachingIntegrationTest {
    private AnnotationConfigApplicationContext context;
    private AlertRuleService rules;
    private AlertRuleRepository repository;
    private CacheManager cacheManager;
    private AlertRule first;
    private AlertRule second;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        repository = mock(AlertRuleRepository.class);
        RedisTemplate<String, Object> redis = mock(RedisTemplate.class);
        ValueOperations<String, Object> values = mock(ValueOperations.class);
        SetOperations<String, Object> sets = mock(SetOperations.class);
        Map<String, Object> stored = new HashMap<>();
        Map<String, Set<Object>> indices = new HashMap<>();
        when(redis.opsForValue()).thenReturn(values);
        when(redis.opsForSet()).thenReturn(sets);
        when(values.get(anyString())).thenAnswer(invocation -> stored.get(invocation.getArgument(0)));
        doAnswer(invocation -> {
            stored.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(values).set(anyString(), any(), anyLong(), any(TimeUnit.class));
        when(sets.add(anyString(), any())).thenAnswer(invocation -> {
            indices.computeIfAbsent(invocation.getArgument(0), key -> new HashSet<>())
                .add(invocation.getArgument(1));
            return 1L;
        });
        when(redis.delete(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            indices.remove(key);
            return stored.remove(key) != null;
        });
        when(redis.delete(anyCollection())).thenAnswer(invocation -> {
            ((java.util.Collection<String>) invocation.getArgument(0)).forEach(stored::remove);
            return 1L;
        });
        when(redis.scan(any(ScanOptions.class))).thenAnswer(invocation -> {
            String pattern = ((ScanOptions) invocation.getArgument(0)).getPattern();
            var keys = stored.keySet().stream()
                .filter(key -> pattern != null && key.startsWith(pattern.replace("*", "")))
                .toList().iterator();
            Cursor<String> cursor = mock(Cursor.class);
            when(cursor.hasNext()).thenAnswer(ignored -> keys.hasNext());
            when(cursor.next()).thenAnswer(ignored -> keys.next());
            return cursor;
        });

        ConcurrentMapCacheManager caches = new ConcurrentMapCacheManager();
        caches.setAllowNullValues(false); // Same null policy as production RedisCacheManager.
        cacheManager = caches;
        context = new AnnotationConfigApplicationContext();
        context.register(ProxyConfiguration.class);
        context.registerBean(CacheManager.class, () -> caches);
        context.registerBean(AlertRuleRepository.class, () -> repository);
        context.registerBean(RedisTemplate.class, () -> redis);
        context.registerBean(ObjectMapper.class, () -> new ObjectMapper());
        context.registerBean(Clock.class, () -> Clock.fixed(
            Instant.parse("2026-10-21T12:00:00Z"), ZoneOffset.UTC));
        context.registerBean(RedisKeyGenerator.class);
        context.registerBean(AlertRuleCacheService.class);
        context.registerBean(VehicleRuleCacheService.class);
        context.registerBean(GeofenceRuleValidator.class, () -> mock(GeofenceRuleValidator.class));
        context.registerBean(RuleParametersMapper.class);
        context.registerBean(AlertRuleService.class);
        context.refresh();
        rules = context.getBean(AlertRuleService.class);
        assertThat(AopUtils.isAopProxy(rules)).isTrue();
        first = rule(1L, "First tenant");
        second = rule(2L, "Second tenant");
        when(repository.findByRuleKeyAndOrganizationId("shared-key", 1L)).thenReturn(Optional.of(first));
        when(repository.findByRuleKeyAndOrganizationId("shared-key", 2L)).thenReturn(Optional.of(second));
        when(repository.save(any(AlertRule.class))).thenAnswer(invocation -> invocation.getArgument(0));
        TenantContext.setCurrentOrgId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        if (context != null) context.close();
    }

    @Test
    void getRuleByKey_sameKeyAcrossTenants_isolatesBothCacheLayers() {
        // given
        assertThat(rules.getRuleByKey("shared-key").getRuleName()).isEqualTo("First tenant");
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());

        // when: bypass the Spring hit so the manual Redis layer is exercised too.
        TenantContext.setCurrentOrgId(2L);
        var result = rules.getRuleByKey("shared-key");

        // then
        assertThat(result.getRuleName()).isEqualTo("Second tenant");
        verify(repository).findByRuleKeyAndOrganizationId("shared-key", 2L);
        TenantContext.setCurrentOrgId(1L);
        assertThat(rules.getRuleByKey("shared-key").getRuleName()).isEqualTo("First tenant");
    }

    @Test
    void getAllRules_sameQueryAcrossTenants_isolatesCachedLists() {
        // given
        when(repository.findByOrganizationId(eq(1L), any())).thenReturn(new PageImpl<>(List.of(first)));
        when(repository.findByOrganizationId(eq(2L), any())).thenReturn(new PageImpl<>(List.of(second)));
        rules.getAllRules();

        // when
        TenantContext.setCurrentOrgId(2L);
        var result = rules.getAllRules();

        // then
        assertThat(result).extracting(response -> response.getRuleName()).containsExactly("Second tenant");
        rules.getAllRules();
        verify(repository, times(1)).findByOrganizationId(eq(2L), any());
    }

    @Test
    void getEnabledRules_realProxy_resolvesKeyAndSeparatesTenants() {
        // given
        when(repository.findByIsEnabledAndOrganizationId(true, 1L)).thenReturn(List.of(first));
        when(repository.findByIsEnabledAndOrganizationId(true, 2L)).thenReturn(List.of(second));
        rules.getEnabledRules();

        // when
        TenantContext.setCurrentOrgId(2L);
        var result = rules.getEnabledRules();

        // then
        assertThat(result).extracting(response -> response.getRuleName()).containsExactly("Second tenant");
        rules.getEnabledRules();
        verify(repository, times(1)).findByIsEnabledAndOrganizationId(true, 2L);
    }

    @Test
    void getAllRulesPaged_equivalentRequests_reusesOnlyCurrentTenantCache() {
        // given
        when(repository.searchAlertRules(eq(1L), any(), any(), any(), any()))
            .thenReturn(new PageImpl<>(List.of(first)));
        when(repository.searchAlertRules(eq(2L), any(), any(), any(), any()))
            .thenReturn(new PageImpl<>(List.of(second)));
        rules.getAllRulesPaged(new SearchRequest());
        rules.getAllRulesPaged(new SearchRequest());

        // when
        TenantContext.setCurrentOrgId(2L);
        var result = rules.getAllRulesPaged(new SearchRequest());

        // then
        assertThat(result.getContent()).extracting(response -> response.getRuleName()).containsExactly("Second tenant");
        verify(repository, times(1)).searchAlertRules(eq(1L), any(), any(), any(), any());
        verify(repository, times(1)).searchAlertRules(eq(2L), any(), any(), any(), any());
    }

    @Test
    void getAllRulesPaged_collidingSearchHashes_keepsDistinctResults() {
        // given: Java's String.hashCode collides for these equal-length inputs.
        second.setOrganizationId(1L);
        when(repository.searchAlertRules(eq(1L), eq("Aa"), any(), any(), any()))
            .thenReturn(new PageImpl<>(List.of(first)));
        when(repository.searchAlertRules(eq(1L), eq("BB"), any(), any(), any()))
            .thenReturn(new PageImpl<>(List.of(second)));
        SearchRequest a = new SearchRequest();
        a.setSearch("Aa");
        SearchRequest b = new SearchRequest();
        b.setSearch("BB");
        rules.getAllRulesPaged(a);

        // when
        var result = rules.getAllRulesPaged(b);

        // then
        assertThat(result.getContent()).extracting(response -> response.getRuleName()).containsExactly("Second tenant");
    }

    @Test
    void getRulesByVehicleIds_collidingSetHashes_keepsDistinctResults() {
        // given
        second.setOrganizationId(1L);
        when(repository.findActiveRulesForVehicleAndOrganizationId("Aa", 1L)).thenReturn(List.of(first));
        when(repository.findActiveRulesForVehicleAndOrganizationId("BB", 1L)).thenReturn(List.of(second));
        rules.getRulesByVehicleIds(Set.of("Aa"));

        // when
        var result = rules.getRulesByVehicleIds(Set.of("BB"));

        // then
        assertThat(result).containsOnlyKeys("BB");
    }

    @Test
    void updateRule_warmCaches_invalidatesOnlyWritingTenant() {
        // given
        when(repository.findByOrganizationId(eq(1L), any())).thenReturn(new PageImpl<>(List.of(first)));
        when(repository.findByOrganizationId(eq(2L), any())).thenReturn(new PageImpl<>(List.of(second)));
        when(repository.searchAlertRules(eq(2L), any(), any(), any(), any()))
            .thenReturn(new PageImpl<>(List.of(second)));
        rules.getAllRules();
        rules.getRuleByKey("shared-key");
        TenantContext.setCurrentOrgId(2L);
        rules.getAllRules();
        rules.getAllRulesPaged(new SearchRequest());
        TenantContext.setCurrentOrgId(1L);
        UpdateAlertRuleRequest update = new UpdateAlertRuleRequest();
        update.setRuleName("Updated tenant one");

        // when
        rules.updateRule("shared-key", update);

        // then
        assertThat(rules.getAllRules()).extracting(response -> response.getRuleName())
            .containsExactly("Updated tenant one");
        assertThat(rules.getRuleByKey("shared-key").getRuleName()).isEqualTo("Updated tenant one");
        TenantContext.setCurrentOrgId(2L);
        assertThat(rules.getAllRules()).extracting(response -> response.getRuleName()).containsExactly("Second tenant");
        verify(repository, times(1)).findByOrganizationId(eq(2L), any());
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
        assertThat(rules.getAllRulesPaged(new SearchRequest()).getContent())
            .extracting(response -> response.getRuleName()).containsExactly("Second tenant");
        verify(repository, times(1)).searchAlertRules(eq(2L), any(), any(), any(), any());
    }

    @Test
    void updateRule_renamedKey_evictsOldDetailAndLists() {
        // given
        rules.getRuleByKey("shared-key");
        UpdateAlertRuleRequest update = new UpdateAlertRuleRequest();
        update.setRuleKey("renamed-key");

        // when
        rules.updateRule("shared-key", update);
        when(repository.findByRuleKeyAndOrganizationId("shared-key", 1L)).thenReturn(Optional.empty());
        when(repository.findByRuleKeyAndOrganizationId("renamed-key", 1L)).thenReturn(Optional.of(first));

        // then
        assertThatThrownBy(() -> rules.getRuleByKey("shared-key")).isInstanceOf(AlertException.class);
        assertThat(rules.getRuleByKey("renamed-key").getRuleKey()).isEqualTo("renamed-key");
    }

    @Test
    void enableRule_voidMutation_evictsRatherThanCachingNull() {
        // given
        first.setIsEnabled(false);
        when(repository.findByIsEnabledAndOrganizationId(true, 1L)).thenReturn(List.of());
        assertThat(rules.getEnabledRules()).isEmpty();

        // when
        rules.enableRule("shared-key");
        when(repository.findByIsEnabledAndOrganizationId(true, 1L)).thenReturn(List.of(first));

        // then
        assertThat(rules.getEnabledRules()).hasSize(1);
    }

    @Test
    void getRuleByKey_missingTenant_rejectsBeforeRepositoryAccess() {
        // given
        TenantContext.clear();

        // when / then
        assertThatThrownBy(() -> rules.getRuleByKey("shared-key")).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void getEnabledRulesPaged_repeatedMutableRequest_usesStableEnabledKey() {
        // given
        when(repository.searchAlertRules(eq(1L), any(), any(), eq(true), any()))
            .thenReturn(new PageImpl<>(List.of(first)));
        SearchRequest request = new SearchRequest();

        // when
        rules.getEnabledRulesPaged(request);
        rules.getEnabledRulesPaged(request);
        rules.getEnabledRulesPaged(new SearchRequest());

        // then
        verify(repository, times(1)).searchAlertRules(eq(1L), any(), any(), eq(true), any());
    }

    @Test
    void getRulesByVehicleIds_sameVehiclesAcrossTenants_isolatesGroupedResults() {
        // given
        when(repository.findActiveRulesForVehicleAndOrganizationId("vehicle-001", 1L)).thenReturn(List.of(first));
        when(repository.findActiveRulesForVehicleAndOrganizationId("vehicle-001", 2L)).thenReturn(List.of(second));
        rules.getRulesByVehicleIds(Set.of("vehicle-001"));

        // when
        TenantContext.setCurrentOrgId(2L);
        var result = rules.getRulesByVehicleIds(Set.of("vehicle-001"));

        // then
        assertThat(result.get("vehicle-001")).extracting(response -> response.getRuleName())
            .containsExactly("Second tenant");
    }

    @ParameterizedTest
    @ValueSource(strings = {"create", "update", "enable", "disable", "delete", "batchCreate", "batchEnable"})
    void mutateRule_warmQueryCaches_invalidatesEveryQueryShape(String mutation) {
        // given
        when(repository.findByOrganizationId(eq(1L), any())).thenReturn(new PageImpl<>(List.of(first)));
        when(repository.findByIsEnabledAndOrganizationId(true, 1L)).thenReturn(List.of(first));
        when(repository.searchAlertRules(eq(1L), any(), any(), any(), any()))
            .thenReturn(new PageImpl<>(List.of(first)));
        when(repository.findActiveRulesForVehicleAndOrganizationId("vehicle-001", 1L)).thenReturn(List.of(first));
        rules.getAllRules();
        rules.getEnabledRules();
        rules.getAllRulesPaged(new SearchRequest());
        rules.getEnabledRulesPaged(new SearchRequest());
        rules.getRulesByVehicleIds(Set.of("vehicle-001"));
        CreateAlertRuleRequest create = new CreateAlertRuleRequest();
        create.setRuleKey("new-key");
        create.setRuleName("New rule");
        create.setRuleType("SPEED");
        create.setParameters("{\"speedLimit\":80,\"vehicleIds\":[\"vehicle-001\"]}");
        UpdateAlertRuleRequest update = new UpdateAlertRuleRequest();
        update.setRuleName("Changed");

        // when
        switch (mutation) {
            case "create" -> rules.createRule(create);
            case "update" -> rules.updateRule("shared-key", update);
            case "enable" -> { first.setIsEnabled(false); rules.enableRule("shared-key"); }
            case "disable" -> rules.disableRule("shared-key");
            case "delete" -> rules.deleteRule("shared-key");
            case "batchCreate" -> rules.batchCreateRules(List.of(create));
            case "batchEnable" -> { first.setIsEnabled(false); rules.batchEnableRules(Set.of("shared-key")); }
            default -> throw new AssertionError("Unexpected mutation");
        }
        first.setRuleName("Changed");

        // then: all previous cached response snapshots must be discarded.
        assertThat(rules.getAllRules()).extracting(response -> response.getRuleName()).containsExactly("Changed");
        assertThat(rules.getEnabledRules()).extracting(response -> response.getRuleName()).containsExactly("Changed");
        assertThat(rules.getAllRulesPaged(new SearchRequest()).getContent())
            .extracting(response -> response.getRuleName()).containsExactly("Changed");
        assertThat(rules.getEnabledRulesPaged(new SearchRequest()).getContent())
            .extracting(response -> response.getRuleName()).containsExactly("Changed");
        assertThat(rules.getRulesByVehicleIds(Set.of("vehicle-001")).get("vehicle-001"))
            .extracting(response -> response.getRuleName()).containsExactly("Changed");
    }

    private AlertRule rule(Long organizationId, String name) {
        AlertRule rule = AlertRuleTestBuilder.defaultRule().ruleKey("shared-key").ruleName(name)
            .organizationId(organizationId).vehicleId("vehicle-001").build();
        rule.setCreatedAt(Instant.parse("2026-10-21T12:00:00Z"));
        rule.setUpdatedAt(rule.getCreatedAt());
        return rule;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableCaching
    @ComponentScan(basePackages = "com.jjenus.tracker.alerting.infrastructure.cache",
        useDefaultFilters = false, includeFilters = @ComponentScan.Filter(type = FilterType.REGEX,
        pattern = ".*\\.(TenantAlertRuleCacheResolver|AlertRuleCacheKeyGenerator)"))
    static class ProxyConfiguration {}
}
