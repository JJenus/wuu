package com.surf.tracker.alerting.infrastructure.cache;

import com.surf.tracker.alerting.domain.AlertRuleTestBuilder;
import com.surf.tracker.alerting.domain.entity.AlertRule;
import com.surf.tracker.alerting.infrastructure.repository.AlertRuleRepository;
import com.surf.tracker.shared.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.connection.DataType;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VehicleRuleCacheServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private AlertRuleRepository ruleRepository;

    @Mock
    private RedisKeyGenerator keyGenerator;

    @Mock
    private ListOperations<String, Object> listOperations;

    @Mock
    private SetOperations<String, Object> setOperations;

    private VehicleRuleCacheService vehicleRuleCacheService;

    private AlertRule testRule;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentOrgId(1L);
        vehicleRuleCacheService = new VehicleRuleCacheService(
            redisTemplate, ruleRepository, keyGenerator
        );
        testRule = AlertRuleTestBuilder.defaultRule()
            .ruleKey("test-rule")
            .vehicleId("vehicle-001")
            .build();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void getActiveRulesForVehicle_cacheHit_returnsCachedRules() {
        // given
        when(redisTemplate.opsForList()).thenReturn(listOperations);
        when(keyGenerator.getVehicleRulesKey("vehicle-001", 1L)).thenReturn("cache-key");
        when(listOperations.range("cache-key", 0, -1))
            .thenReturn(List.of(testRule));

        // when
        List<AlertRule> result = vehicleRuleCacheService.getActiveRulesForVehicle("vehicle-001", 1L);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getRuleKey()).isEqualTo("test-rule");
    }

    @Test
    void getActiveRulesForVehicle_cacheMiss_loadsFromDb() {
        // given
        when(redisTemplate.opsForList()).thenReturn(listOperations);
        when(keyGenerator.getVehicleRulesKey("vehicle-001", 1L)).thenReturn("cache-key");
        when(listOperations.range("cache-key", 0, -1)).thenReturn(Collections.emptyList());
        when(ruleRepository.findActiveRulesForVehicleAndOrganizationId("vehicle-001", 1L)).thenReturn(List.of(testRule));

        // when
        List<AlertRule> result = vehicleRuleCacheService.getActiveRulesForVehicle("vehicle-001", 1L);

        // then
        assertThat(result).hasSize(1);
        verify(ruleRepository).findActiveRulesForVehicleAndOrganizationId("vehicle-001", 1L);
    }

    @Test
    void hasActiveRules_cacheHitEmpty_returnsFalse() {
        // given
        when(redisTemplate.opsForValue()).thenReturn(mock(org.springframework.data.redis.core.ValueOperations.class));
        when(keyGenerator.getVehicleRulesKey("vehicle-001", 1L)).thenReturn("cache-key");
        when(redisTemplate.type("cache-key")).thenReturn(DataType.STRING);
        when(redisTemplate.opsForValue().get("cache-key")).thenReturn("EMPTY");

        // when
        boolean result = vehicleRuleCacheService.hasActiveRules("vehicle-001", 1L);

        // then
        assertThat(result).isFalse();
    }

    @Test
    void hasActiveRules_cacheHitHasRules_returnsTrue() {
        // given
        when(keyGenerator.getVehicleRulesKey("vehicle-001", 1L)).thenReturn("cache-key");
        when(redisTemplate.opsForList()).thenReturn(listOperations);
        when(listOperations.range("cache-key", 0, -1)).thenReturn(List.of(testRule));

        // when
        boolean result = vehicleRuleCacheService.hasActiveRules("vehicle-001", 1L);

        // then
        assertThat(result).isTrue();
    }

    @Test
    void hasRulesCached_vehicleInIndex_returnsTrue() {
        // given
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(keyGenerator.getVehiclesWithRulesKey()).thenReturn("index-key");
        when(setOperations.isMember("index-key", "vehicle-001")).thenReturn(true);

        // when
        boolean result = vehicleRuleCacheService.hasRulesCached("vehicle-001");

        // then
        assertThat(result).isTrue();
    }

    @Test
    void invalidateVehicleRules_clearsCacheAndIndex() {
        // given
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(keyGenerator.getVehicleRulesKey("vehicle-001")).thenReturn("rules-key");
        when(keyGenerator.getVehiclesWithRulesKey()).thenReturn("index-key");

        // when
        vehicleRuleCacheService.invalidateVehicleRules("vehicle-001");

        // then
        verify(redisTemplate).delete("rules-key");
        verify(setOperations).remove("index-key", "vehicle-001");
    }

    @Test
    void getVehiclesWithActiveRules_loadsFromRepository() {
        // given
        Set<String> vehicleIds = Set.of("vehicle-001", "vehicle-002");
        when(ruleRepository.findVehiclesWithActiveRules(1L)).thenReturn(vehicleIds);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(keyGenerator.getVehiclesWithRulesKey(1L)).thenReturn("index-key");

        // when
        Set<String> result = vehicleRuleCacheService.getVehiclesWithActiveRules(1L);

        // then
        assertThat(result).containsExactlyInAnyOrder("vehicle-001", "vehicle-002");
        verify(ruleRepository).findVehiclesWithActiveRules(1L);
    }

    @Test
    void getActiveRulesForVehicle_exception_returnsEmptyList() {
        // given
        when(redisTemplate.opsForList()).thenThrow(new RuntimeException("Redis error"));

        // when
        List<AlertRule> result = vehicleRuleCacheService.getActiveRulesForVehicle("vehicle-001", 1L);

        // then
        assertThat(result).isEmpty();
    }
}
