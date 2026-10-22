package com.jjenus.tracker.alerting.infrastructure.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjenus.tracker.alerting.domain.AlertRuleTestBuilder;
import com.jjenus.tracker.alerting.domain.entity.AlertRule;
import com.jjenus.tracker.alerting.infrastructure.repository.AlertRuleRepository;
import com.jjenus.tracker.shared.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.connection.DataType;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisStringCommands;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RuleRedisTenantCacheTest {
    @Mock private RedisTemplate<String, Object> redis;
    @Mock private AlertRuleRepository repository;
    @Mock private ListOperations<String, Object> lists;
    @Mock private ValueOperations<String, Object> values;
    @Mock private SetOperations<String, Object> sets;
    private final RedisKeyGenerator keys = new RedisKeyGenerator();

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentOrgId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void getActiveRulesForVehicle_explicitOrganization_isolatesReadAndWriteKeys() {
        // given: background callers may supply an organization unlike the HTTP context.
        VehicleRuleCacheService cache = new VehicleRuleCacheService(redis, repository, keys);
        Map<String, List<Object>> stored = new HashMap<>();
        when(redis.opsForList()).thenReturn(lists);
        when(lists.range(anyString(), eq(0L), eq(-1L)))
            .thenAnswer(invocation -> stored.get(invocation.getArgument(0)));
        when(lists.rightPushAll(anyString(), any(Object[].class))).thenAnswer(invocation -> {
            stored.put(invocation.getArgument(0), List.of((Object[]) invocation.getRawArguments()[1]));
            return 1L;
        });
        AlertRule first = AlertRuleTestBuilder.defaultRule().organizationId(1L).build();
        AlertRule second = AlertRuleTestBuilder.defaultRule().organizationId(2L).build();
        when(repository.findActiveRulesForVehicleAndOrganizationId("same-vehicle", 1L)).thenReturn(List.of(first));
        when(repository.findActiveRulesForVehicleAndOrganizationId("same-vehicle", 2L)).thenReturn(List.of(second));

        // when
        cache.getActiveRulesForVehicle("same-vehicle", 1L);
        var result = cache.getActiveRulesForVehicle("same-vehicle", 2L);

        // then
        assertThat(result).containsExactly(second);
        assertThat(stored).hasSize(2);
        assertThat(cache.getActiveRulesForVehicle("same-vehicle", 1L)).containsExactly(first);
        verify(repository, times(1)).findActiveRulesForVehicleAndOrganizationId("same-vehicle", 1L);
    }

    @Test
    void getRuleByKey_foreignTenantPayload_isNeverReturned() {
        // given
        AlertRuleCacheService cache = new AlertRuleCacheService(redis, repository, keys, new ObjectMapper());
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(keys.getRuleDetailKey("same-rule")))
            .thenReturn(AlertRuleTestBuilder.defaultRule().organizationId(2L).build());

        // when / then
        assertThat(cache.getRuleByKey("same-rule")).isEmpty();
        verify(redis, never()).expire(anyString(), anyLong(), any(TimeUnit.class));
    }

    @Test
    void cacheRule_explicitOrganization_usesRuleOwnerAndRemovesDisabledIndexEntry() {
        // given
        AlertRuleCacheService cache = new AlertRuleCacheService(redis, repository, keys, new ObjectMapper());
        AlertRule rule = AlertRuleTestBuilder.defaultRule().ruleKey("same-rule").organizationId(2L)
            .enabled(false).build();
        when(redis.opsForValue()).thenReturn(values);
        when(redis.opsForSet()).thenReturn(sets);

        // when
        cache.cacheRule(rule);

        // then
        verify(values).set(keys.getRuleDetailKey("same-rule", 2L), rule,
            RedisKeyGenerator.RULE_DETAIL_TTL, TimeUnit.SECONDS);
        verify(sets).remove(keys.getAllActiveRulesKey(2L), "same-rule");
    }

    @Test
    void invalidateVehicleRules_currentTenant_neverTouchesOtherTenantKeys() {
        // given
        VehicleRuleCacheService cache = new VehicleRuleCacheService(redis, repository, keys);
        when(redis.opsForSet()).thenReturn(sets);

        // when
        cache.invalidateVehicleRules("same-vehicle");

        // then
        verify(redis).delete(keys.getVehicleRulesKey("same-vehicle", 1L));
        verify(redis, never()).delete(keys.getVehicleRulesKey("same-vehicle", 2L));
        verify(sets).remove(keys.getVehiclesWithRulesKey(1L), "same-vehicle");
        verifyNoMoreInteractions(sets);
    }

    @Test
    void getVehiclesWithActiveRules_explicitOrganization_scopesIndexWrites() {
        // given
        VehicleRuleCacheService cache = new VehicleRuleCacheService(redis, repository, keys);
        when(repository.findVehiclesWithActiveRules(2L)).thenReturn(Set.of("same-vehicle"));
        when(redis.opsForSet()).thenReturn(sets);

        // when
        cache.getVehiclesWithActiveRules(2L);

        // then
        verify(redis).delete(keys.getVehiclesWithRulesKey(2L));
        verify(redis, never()).delete(keys.getVehiclesWithRulesKey(1L));
    }

    @Test
    void ruleKeys_missingTenant_failClosed() {
        // given
        TenantContext.clear();

        // when / then
        assertThatThrownBy(() -> keys.getRuleDetailKey("same-rule")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> keys.getVehicleRulesKey("same-vehicle")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(keys::getAllRuleKeysPattern).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void hasRulesCached_coldIndex_loadsRulesInsteadOfSkippingVehicle() {
        // given
        VehicleRuleCacheService cache = new VehicleRuleCacheService(redis, repository, keys);
        AlertRule rule = AlertRuleTestBuilder.defaultRule().organizationId(1L).build();
        when(redis.opsForSet()).thenReturn(sets);
        when(redis.opsForList()).thenReturn(lists);
        when(lists.range(keys.getVehicleRulesKey("same-vehicle"), 0, -1)).thenReturn(List.of());
        when(repository.findActiveRulesForVehicleAndOrganizationId("same-vehicle", 1L)).thenReturn(List.of(rule));

        // when / then
        assertThat(cache.hasRulesCached("same-vehicle")).isTrue();
    }

    @Test
    void hasActiveRules_positiveRedisList_neverIssuesStringGet() {
        // given: a real Redis server rejects GET against a LIST key.
        VehicleRuleCacheService cache = new VehicleRuleCacheService(redis, repository, keys);
        AlertRule rule = AlertRuleTestBuilder.defaultRule().organizationId(1L).build();
        when(redis.opsForList()).thenReturn(lists);
        when(lists.range(keys.getVehicleRulesKey("same-vehicle"), 0, -1)).thenReturn(List.of(rule));

        // when / then
        assertThat(cache.hasActiveRules("same-vehicle", 1L)).isTrue();
        verify(redis, never()).opsForValue();
        verifyNoInteractions(repository);
    }

    @Test
    void getActiveRulesForVehicle_emptyStringMarker_neverIssuesListRange() {
        // given
        VehicleRuleCacheService cache = new VehicleRuleCacheService(redis, repository, keys);
        String key = keys.getVehicleRulesKey("same-vehicle");
        when(redis.type(key)).thenReturn(DataType.STRING);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(key)).thenReturn("EMPTY");

        // when / then
        assertThat(cache.getActiveRulesForVehicle("same-vehicle", 1L)).isEmpty();
        verify(redis, never()).opsForList();
        verifyNoInteractions(repository);
    }

    @Test
    void getAllActiveRules_explicitOrganization_scopesPipelineAndRejectsForeignPayloads() {
        // given
        AlertRuleCacheService cache = new AlertRuleCacheService(redis, repository, keys, new ObjectMapper());
        RedisConnection connection = mock(RedisConnection.class);
        RedisStringCommands strings = mock(RedisStringCommands.class);
        when(connection.stringCommands()).thenReturn(strings);
        when(redis.opsForSet()).thenReturn(sets);
        when(sets.members(keys.getAllActiveRulesKey(2L))).thenReturn(Set.of("same-rule"));
        when(redis.executePipelined(any(RedisCallback.class))).thenAnswer(invocation -> {
            ((RedisCallback<?>) invocation.getArgument(0)).doInRedis(connection);
            return List.of(Map.of("organizationId", 1L, "ruleKey", "foreign"),
                Map.of("organizationId", 2L, "ruleKey", "owned"));
        });

        // when
        var result = cache.getAllActiveRules(2L);

        // then
        assertThat(result).extracting(AlertRule::getRuleKey).containsExactly("owned");
        verify(strings).get(keys.getRuleDetailKey("same-rule", 2L)
            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        verifyNoInteractions(repository);
    }

    @Test
    void getAllActiveRules_missingIndex_rebuildsOnlyRequestedOrganization() {
        // given
        AlertRuleCacheService cache = new AlertRuleCacheService(redis, repository, keys, new ObjectMapper());
        AlertRule rule = AlertRuleTestBuilder.defaultRule().ruleKey("same-rule").organizationId(2L).build();
        when(redis.opsForSet()).thenReturn(sets);
        when(redis.opsForValue()).thenReturn(values);
        when(repository.findActiveRulesOrderedByPriority(2L)).thenReturn(List.of(rule));

        // when
        var result = cache.getAllActiveRules(2L);

        // then
        assertThat(result).containsExactly(rule);
        verify(redis).delete(keys.getAllActiveRulesKey(2L));
        verify(values).set(keys.getRuleDetailKey("same-rule", 2L), rule,
            RedisKeyGenerator.RULE_DETAIL_TTL, TimeUnit.SECONDS);
        verify(sets).add(keys.getAllActiveRulesKey(2L), "same-rule");
        verify(redis, never()).delete(keys.getAllActiveRulesKey(1L));
    }

    @Test
    void getAllActiveRules_redisUnavailable_fallsBackToScopedRepository() {
        // given
        AlertRuleCacheService cache = new AlertRuleCacheService(redis, repository, keys, new ObjectMapper());
        AlertRule rule = AlertRuleTestBuilder.defaultRule().organizationId(2L).build();
        when(redis.opsForSet()).thenThrow(new IllegalStateException("Redis unavailable"));
        when(repository.findActiveRulesOrderedByPriority(2L)).thenReturn(List.of(rule));

        // when / then
        assertThat(cache.getAllActiveRules(2L)).containsExactly(rule);
        verify(repository, never()).findActiveRulesOrderedByPriority(1L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void clearAllCache_currentOrganization_scansOnlyItsRuleNamespace() {
        // given
        AlertRuleCacheService cache = new AlertRuleCacheService(redis, repository, keys, new ObjectMapper());
        Cursor<String> cursor = mock(Cursor.class);
        when(redis.scan(any(ScanOptions.class))).thenReturn(cursor);
        when(cursor.hasNext()).thenReturn(true, false);
        when(cursor.next()).thenReturn(keys.getRuleDetailKey("same-rule"));

        // when
        cache.clearAllCache();

        // then
        var options = org.mockito.ArgumentCaptor.forClass(ScanOptions.class);
        verify(redis).scan(options.capture());
        assertThat(options.getValue().getPattern()).isEqualTo("alerting:rule:v2:org:1:*");
        verify(redis).delete(List.of(keys.getRuleDetailKey("same-rule", 1L)));
    }
}
