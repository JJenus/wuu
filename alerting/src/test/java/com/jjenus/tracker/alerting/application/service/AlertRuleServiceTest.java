package com.jjenus.tracker.alerting.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjenus.tracker.alerting.api.dto.*;
import com.jjenus.tracker.alerting.domain.AlertRuleTestBuilder;
import com.jjenus.tracker.alerting.domain.GeofenceTestBuilder;
import com.jjenus.tracker.alerting.domain.entity.AlertRule;
import com.jjenus.tracker.alerting.domain.entity.Geofence;
import com.jjenus.tracker.alerting.domain.enums.AlertRuleType;
import com.jjenus.tracker.alerting.domain.parameters.RuleParametersMapper;
import com.jjenus.tracker.alerting.exception.AlertException;
import com.jjenus.tracker.alerting.infrastructure.cache.AlertRuleCacheService;
import com.jjenus.tracker.alerting.infrastructure.cache.RedisKeyGenerator;
import com.jjenus.tracker.alerting.infrastructure.cache.VehicleRuleCacheService;
import com.jjenus.tracker.alerting.infrastructure.repository.AlertRuleRepository;
import com.jjenus.tracker.shared.security.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlertRuleServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-08-30T12:00:00Z"), ZoneOffset.UTC);

    @Mock
    private AlertRuleRepository ruleRepository;

    @Mock
    private AlertRuleCacheService ruleCacheService;

    @Mock
    private VehicleRuleCacheService vehicleRuleCacheService;

    @Mock
    private GeofenceRuleValidator geofenceRuleValidator;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private RedisKeyGenerator keyGenerator;

    private AlertRuleService alertRuleService;

    private AlertRule testRule;
    private Geofence testGeofence;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentOrgId(1L);
        alertRuleService = new AlertRuleService(
            ruleRepository,
            ruleCacheService,
            vehicleRuleCacheService,
            geofenceRuleValidator,
            objectMapper,
            redisTemplate,
            keyGenerator,
            new RuleParametersMapper(new ObjectMapper()),
            FIXED_CLOCK
        );

        testRule = AlertRuleTestBuilder.defaultRule()
            .ruleKey("test-rule")
            .vehicleId("vehicle-001")
            .build();

        testGeofence = GeofenceTestBuilder.defaultGeofence()
            .geofenceId(1L)
            .vehicleId("vehicle-001")
            .build();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void createRule_validRequest_preservesTenantVehiclesAndTimestamps() throws JsonProcessingException {
        // given
        CreateAlertRuleRequest request = new CreateAlertRuleRequest();
        request.setRuleKey("tenant-rule");
        request.setRuleName("Tenant rule");
        request.setRuleType("speed");
        request.setParameters("{\"speedLimit\":80,\"vehicleIds\":[\"vehicle-001\"]}");
        request.setPriority(3);
        request.setEnabled(true);
        when(objectMapper.readValue(anyString(), any(com.fasterxml.jackson.core.type.TypeReference.class)))
            .thenReturn(Map.of("speedLimit", 80, "vehicleIds", List.of("vehicle-001")));
        when(ruleRepository.save(any(AlertRule.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        AlertRuleResponse result = alertRuleService.createRule(request);

        // then
        var saved = org.mockito.ArgumentCaptor.forClass(AlertRule.class);
        verify(ruleRepository).save(saved.capture());
        assertThat(saved.getValue().getOrganizationId()).isEqualTo(1L);
        assertThat(saved.getValue().getVehicleIds()).containsExactly("vehicle-001");
        assertThat(result.getRuleKey()).isEqualTo("tenant-rule");
        assertThat(result.getRuleType()).isEqualTo(AlertRuleType.SPEED);
        assertThat(result.getPriority()).isEqualTo(3);
        assertThat(result.isEnabled()).isTrue();
        // Custom rules retain the entity's existing creation timestamp default.
        assertThat(result.getCreatedAt()).isEqualTo(Instant.EPOCH);
        assertThat(result.getUpdatedAt()).isEqualTo(FIXED_CLOCK.instant());
        verify(ruleCacheService).cacheRule(saved.getValue());
        verify(vehicleRuleCacheService).invalidateVehicleRules("vehicle-001");
    }

    @Test
    void getAllRules_multiplePages_readsOnlyCurrentOrganization() {
        // given
        var first = PageRequest.of(0, 500);
        var second = PageRequest.of(1, 500);
        when(ruleRepository.findByOrganizationId(1L, first))
            .thenReturn(new PageImpl<>(List.of(testRule), first, 501));
        when(ruleRepository.findByOrganizationId(1L, second))
            .thenReturn(new PageImpl<>(List.of(testRule), second, 501));

        // when
        List<AlertRuleResponse> result = alertRuleService.getAllRules();

        // then
        assertThat(result).extracting(AlertRuleResponse::getRuleKey)
            .containsExactly("test-rule", "test-rule");
        verify(ruleRepository, never()).findAll(any(org.springframework.data.domain.Pageable.class));
    }

    @Test
    void getEnabledRules_currentOrganization_returnsEnabledRules() {
        // given
        when(ruleRepository.findByIsEnabledAndOrganizationId(true, 1L)).thenReturn(List.of(testRule));

        // when
        List<AlertRuleResponse> result = alertRuleService.getEnabledRules();

        // then
        assertThat(result).extracting(AlertRuleResponse::getRuleKey).containsExactly("test-rule");
    }

    @Test
    void getRuleByKey_cachedRule_avoidsRepositoryLookup() {
        // given
        when(ruleCacheService.getRuleByKey("test-rule")).thenReturn(Optional.of(testRule));

        // when
        AlertRuleResponse result = alertRuleService.getRuleByKey("test-rule");

        // then
        assertThat(result.getRuleKey()).isEqualTo("test-rule");
        verifyNoInteractions(ruleRepository);
    }

    @Test
    void getAllRulesPaged_cachedPage_avoidsRepositoryLookup() {
        // given
        SearchRequest request = new SearchRequest();
        PagedResponse<AlertRuleResponse> cached = new PagedResponse<>(Page.empty());
        var values = mock(org.springframework.data.redis.core.ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(keyGenerator.getPaginatedRulesKey(anyInt(), anyInt(), anyString(), anyString(), any(), any(), any()))
            .thenReturn("cached-page");
        when(values.get("cached-page")).thenReturn(cached);

        // when
        PagedResponse<AlertRuleResponse> result = alertRuleService.getAllRulesPaged(request);

        // then
        assertThat(result).isSameAs(cached);
        verifyNoInteractions(ruleRepository);
    }

    @Test
    void getAllRulesPaged_cacheUnavailable_returnsRepositoryResults() {
        // given
        SearchRequest request = new SearchRequest();
        when(redisTemplate.opsForValue()).thenThrow(new IllegalStateException("Redis unavailable"));
        when(ruleRepository.searchAlertRules(eq(1L), any(), any(), any(), any()))
            .thenReturn(new PageImpl<>(List.of(testRule)));

        // when
        PagedResponse<AlertRuleResponse> result = alertRuleService.getAllRulesPaged(request);

        // then
        assertThat(result.getContent()).extracting(AlertRuleResponse::getRuleKey).containsExactly("test-rule");
    }

    @Test
    void getEnabledRulesPaged_requestWithoutFilter_forcesEnabledFilter() {
        // given
        SearchRequest request = new SearchRequest();
        when(ruleRepository.searchAlertRules(eq(1L), any(), any(), eq(true), any()))
            .thenReturn(new PageImpl<>(List.of(testRule)));

        // when
        PagedResponse<AlertRuleResponse> result = alertRuleService.getEnabledRulesPaged(request);

        // then
        assertThat(result.getContent()).hasSize(1);
        assertThat(request.getEnabled()).isTrue();
    }

    @Test
    void updateRule_vehicleAssociationsChanged_invalidatesOldAndNewVehicles() {
        // given
        testRule.setParameters(new HashMap<>(Map.of("speedLimit", 80)));
        UpdateAlertRuleRequest request = new UpdateAlertRuleRequest();
        request.setParameters(Map.of("vehicleIds", List.of("vehicle-002")));
        when(ruleRepository.findByRuleKeyAndOrganizationId("test-rule", 1L)).thenReturn(Optional.of(testRule));
        when(ruleRepository.save(testRule)).thenReturn(testRule);

        // when
        AlertRuleResponse result = alertRuleService.updateRule("test-rule", request);

        // then
        assertThat(testRule.getVehicleIds()).containsExactly("vehicle-002");
        assertThat(result.getParameters()).containsEntry("speedLimit", 80);
        assertThat(result.getUpdatedAt()).isEqualTo(FIXED_CLOCK.instant());
        verify(vehicleRuleCacheService).invalidateVehicleRules("vehicle-001");
        verify(vehicleRuleCacheService).invalidateVehicleRules("vehicle-002");
    }

    @Test
    void updateRule_emptyRequest_preservesRuleWithoutSaving() {
        // given
        when(ruleRepository.findByRuleKeyAndOrganizationId("test-rule", 1L)).thenReturn(Optional.of(testRule));

        // when
        AlertRuleResponse result = alertRuleService.updateRule("test-rule", new UpdateAlertRuleRequest());

        // then
        assertThat(result.getRuleKey()).isEqualTo("test-rule");
        verify(ruleRepository, never()).save(any());
        verifyNoInteractions(ruleCacheService, vehicleRuleCacheService);
    }

    @Test
    void getRulesByVehicleIds_currentOrganization_groupsRulesIncludingEmptyResults() {
        // given
        when(ruleRepository.findActiveRulesForVehicleAndOrganizationId("vehicle-001", 1L))
            .thenReturn(List.of(testRule));
        when(ruleRepository.findActiveRulesForVehicleAndOrganizationId("vehicle-002", 1L))
            .thenReturn(List.of());

        // when
        Map<String, List<AlertRuleResponse>> result = alertRuleService.getRulesByVehicleIds(
            Set.of("vehicle-001", "vehicle-002"));

        // then
        assertThat(result.get("vehicle-001")).extracting(AlertRuleResponse::getRuleKey).containsExactly("test-rule");
        assertThat(result.get("vehicle-002")).isEmpty();
    }

    @Test
    void batchEnableRules_missingRule_continuesWithRemainingRules() {
        // given
        testRule.setIsEnabled(false);
        when(ruleRepository.findByRuleKeyAndOrganizationId("missing", 1L)).thenReturn(Optional.empty());
        when(ruleRepository.findByRuleKeyAndOrganizationId("test-rule", 1L)).thenReturn(Optional.of(testRule));

        // when
        alertRuleService.batchEnableRules(new LinkedHashSet<>(List.of("missing", "test-rule")));

        // then
        assertThat(testRule.isEnabled()).isTrue();
        verify(ruleRepository).save(testRule);
    }

    @Test
    void createRule_validRequest_returnsCreatedRule() throws JsonProcessingException {
        // given
        CreateAlertRuleRequest request = new CreateAlertRuleRequest();
        request.setRuleKey("new-rule");
        request.setRuleName("New Alert Rule");
        request.setRuleType("SPEED");
        request.setParameters("{\"speedLimit\":80}");
        request.setPriority(1);
        request.setEnabled(true);

        when(ruleRepository.existsByRuleKeyAndOrganizationId("new-rule", 1L)).thenReturn(false);
        when(ruleRepository.save(any(AlertRule.class))).thenReturn(testRule);
        when(objectMapper.readValue(anyString(), any(com.fasterxml.jackson.core.type.TypeReference.class)))
            .thenReturn(Map.of("speedLimit", 80.0f));

        // when
        AlertRuleResponse result = alertRuleService.createRule(request);

        // then
        assertThat(result).isNotNull();
        verify(ruleRepository).save(any(AlertRule.class));
        verify(ruleCacheService).cacheRule(any(AlertRule.class));
    }

    @Test
    void createRule_duplicateRuleKey_throwsException() {
        // given
        CreateAlertRuleRequest request = new CreateAlertRuleRequest();
        request.setRuleKey("existing-rule");

        when(ruleRepository.existsByRuleKeyAndOrganizationId("existing-rule", 1L)).thenReturn(true);

        // when & then
        assertThatThrownBy(() -> alertRuleService.createRule(request))
            .isInstanceOf(AlertException.class)
            .hasMessageContaining("already exists");
    }

    @Test
    void createRule_invalidRuleType_throwsException() {
        // given
        CreateAlertRuleRequest request = new CreateAlertRuleRequest();
        request.setRuleKey("new-rule");
        request.setRuleName("New Rule");
        request.setRuleType("INVALID_TYPE");
        request.setParameters("{}");

        when(ruleRepository.existsByRuleKeyAndOrganizationId("new-rule", 1L)).thenReturn(false);

        // when & then
        assertThatThrownBy(() -> alertRuleService.createRule(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid rule type");
    }

    @Test
    void getRuleByKey_existingRule_returnsRule() {
        // given
        when(ruleCacheService.getRuleByKey("test-rule")).thenReturn(Optional.empty());
        when(ruleRepository.findByRuleKeyAndOrganizationId("test-rule", 1L)).thenReturn(Optional.of(testRule));

        // when
        AlertRuleResponse result = alertRuleService.getRuleByKey("test-rule");

        // then
        assertThat(result).isNotNull();
        assertThat(result.getRuleKey()).isEqualTo("test-rule");
    }

    @Test
    void getRuleByKey_nonExistentRule_throwsException() {
        // given
        when(ruleCacheService.getRuleByKey("non-existent")).thenReturn(Optional.empty());
        when(ruleRepository.findByRuleKeyAndOrganizationId("non-existent", 1L)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> alertRuleService.getRuleByKey("non-existent"))
            .isInstanceOf(AlertException.class)
            .hasMessageContaining("not found");
    }

    @Test
    void getAllRulesPaged_returnsPagedResults() {
        // given
        PageRequest pageable = PageRequest.of(0, 20, Sort.by("createdAt").descending());
        Page<AlertRule> page = new PageImpl<>(List.of(testRule), pageable, 1);

        SearchRequest searchRequest = new SearchRequest();
        searchRequest.setPage(0);
        searchRequest.setSize(20);
        searchRequest.setSortBy("createdAt");
        searchRequest.setSortDirection(Sort.Direction.DESC);

        when(redisTemplate.opsForValue()).thenReturn(mock(org.springframework.data.redis.core.ValueOperations.class));
        when(keyGenerator.getPaginatedRulesKey(anyInt(), anyInt(), anyString(), anyString(), any(), any(), any()))
            .thenReturn("cache-key");
        when(ruleRepository.searchAlertRules(any(), any(), any(), any(), any())).thenReturn(page);

        // when
        PagedResponse<AlertRuleResponse> result = alertRuleService.getAllRulesPaged(searchRequest);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getPageNumber()).isEqualTo(0);
    }

    @Test
    void enableRule_existingRule_enablesIt() {
        // given
        testRule.setIsEnabled(false);
        when(ruleRepository.findByRuleKeyAndOrganizationId("test-rule", 1L)).thenReturn(Optional.of(testRule));

        // when
        alertRuleService.enableRule("test-rule");

        // then
        assertThat(testRule.isEnabled()).isTrue();
        verify(ruleRepository).save(testRule);
        verify(ruleCacheService).cacheRule(testRule);
        verify(vehicleRuleCacheService).invalidateVehicleRules("vehicle-001");
    }

    @Test
    void enableRule_alreadyEnabled_noAction() {
        // given
        testRule.setIsEnabled(true);
        when(ruleRepository.findByRuleKeyAndOrganizationId("test-rule", 1L)).thenReturn(Optional.of(testRule));

        // when
        alertRuleService.enableRule("test-rule");

        // then
        verify(ruleRepository, never()).save(any());
    }

    @Test
    void disableRule_existingRule_disablesIt() {
        // given
        testRule.setIsEnabled(true);
        when(ruleRepository.findByRuleKeyAndOrganizationId("test-rule", 1L)).thenReturn(Optional.of(testRule));

        // when
        alertRuleService.disableRule("test-rule");

        // then
        assertThat(testRule.isEnabled()).isFalse();
        verify(ruleRepository).save(testRule);
        verify(ruleCacheService).evictRule("test-rule");
        verify(vehicleRuleCacheService).invalidateVehicleRules("vehicle-001");
    }

    @Test
    void deleteRule_existingRule_deletesIt() {
        // given
        when(ruleRepository.findByRuleKeyAndOrganizationId("test-rule", 1L)).thenReturn(Optional.of(testRule));
        doNothing().when(ruleRepository).deleteByRuleKeyAndOrganizationId("test-rule", 1L);

        // when
        alertRuleService.deleteRule("test-rule");

        // then
        verify(ruleRepository).deleteByRuleKeyAndOrganizationId("test-rule", 1L);
        verify(ruleCacheService).evictRule("test-rule");
        verify(vehicleRuleCacheService).invalidateVehicleRules("vehicle-001");
    }

    @Test
    void deleteRule_nonExistentRule_throwsException() {
        // given
        when(ruleRepository.findByRuleKeyAndOrganizationId("non-existent", 1L)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> alertRuleService.deleteRule("non-existent"))
            .isInstanceOf(AlertException.class)
            .hasMessageContaining("not found");
    }

    @Test
    void createOverspeedRule_validRequest_returnsCreatedRule() {
        // given
        OverspeedRuleTemplateRequest request = new OverspeedRuleTemplateRequest();
        request.setRuleKey("overspeed-rule");
        request.setRuleName("Overspeed Alert");
        request.setSpeedLimit(80.0f);
        request.setBuffer(5.0f);
        request.setVehicleIds(Set.of("vehicle-001"));
        request.setPriority(1);
        request.setEnabled(true);

        when(ruleRepository.existsByRuleKeyAndOrganizationId("overspeed-rule", 1L)).thenReturn(false);
        when(ruleRepository.save(any(AlertRule.class))).thenReturn(testRule);

        // when
        AlertRuleResponse result = alertRuleService.createOverspeedRule(request);

        // then
        assertThat(result).isNotNull();
        verify(ruleRepository).save(any(AlertRule.class));
    }

    @Test
    void createOverspeedRule_invalidSpeedLimit_throwsException() {
        // given
        OverspeedRuleTemplateRequest request = new OverspeedRuleTemplateRequest();
        request.setSpeedLimit(0.0f);
        request.setVehicleIds(Set.of("vehicle-001"));

        // when & then
        assertThatThrownBy(() -> alertRuleService.createOverspeedRule(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Speed limit must be positive");
    }

    @Test
    void createOverspeedRule_noVehicles_throwsException() {
        // given
        OverspeedRuleTemplateRequest request = new OverspeedRuleTemplateRequest();
        request.setSpeedLimit(80.0f);
        request.setVehicleIds(Collections.emptySet());

        // when & then
        assertThatThrownBy(() -> alertRuleService.createOverspeedRule(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("At least one vehicle ID is required");
    }

    @Test
    void createIdleTimeoutRule_validRequest_returnsCreatedRule() {
        // given
        IdleTimeoutRuleTemplateRequest request = new IdleTimeoutRuleTemplateRequest();
        request.setRuleKey("idle-rule");
        request.setRuleName("Idle Timeout");
        request.setMaxIdleMinutes(30);
        request.setVehicleIds(Set.of("vehicle-001"));
        request.setPriority(2);
        request.setEnabled(true);

        when(ruleRepository.existsByRuleKeyAndOrganizationId("idle-rule", 1L)).thenReturn(false);
        when(ruleRepository.save(any(AlertRule.class))).thenReturn(testRule);

        // when
        AlertRuleResponse result = alertRuleService.createIdleTimeoutRule(request);

        // then
        assertThat(result).isNotNull();
        verify(ruleRepository).save(any(AlertRule.class));
    }

    @Test
    void createGeofenceRule_validRequest_returnsCreatedRule() {
        // given
        GeofenceRuleTemplateRequest request = new GeofenceRuleTemplateRequest();
        request.setRuleKey("geofence-rule");
        request.setRuleName("Geofence Alert");
        request.setGeofenceId("1");
        request.setAction(GeofenceRuleTemplateRequest.GeofenceAction.BOTH);
        request.setVehicleIds(Set.of("vehicle-001"));
        request.setPriority(3);
        request.setEnabled(true);

        when(ruleRepository.existsByRuleKeyAndOrganizationId("geofence-rule", 1L)).thenReturn(false);
        when(geofenceRuleValidator.getValidatedGeofence("1")).thenReturn(testGeofence);
        when(ruleRepository.save(any(AlertRule.class))).thenReturn(testRule);

        // when
        AlertRuleResponse result = alertRuleService.createGeofenceRule(request);

        // then
        assertThat(result).isNotNull();
        verify(ruleRepository).save(any(AlertRule.class));
    }

    @Test
    void updateRule_validRequest_updatesRule() {
        // given
        UpdateAlertRuleRequest request = new UpdateAlertRuleRequest();
        request.setRuleName("Updated Name");
        request.setPriority(10);

        when(ruleRepository.findByRuleKeyAndOrganizationId("test-rule", 1L)).thenReturn(Optional.of(testRule));
        when(ruleRepository.save(any(AlertRule.class))).thenReturn(testRule);

        // when
        AlertRuleResponse result = alertRuleService.updateRule("test-rule", request);

        // then
        assertThat(result).isNotNull();
        assertThat(testRule.getRuleName()).isEqualTo("Updated Name");
        assertThat(testRule.getPriority()).isEqualTo(10);
        verify(ruleRepository).save(testRule);
        verify(ruleCacheService).cacheRule(testRule);
    }

    @Test
    void updateRule_ruleKeyChange_updatesKey() {
        // given
        UpdateAlertRuleRequest request = new UpdateAlertRuleRequest();
        request.setRuleKey("new-key");

        when(ruleRepository.findByRuleKeyAndOrganizationId("test-rule", 1L)).thenReturn(Optional.of(testRule));
        when(ruleRepository.existsByRuleKeyAndOrganizationId("new-key", 1L)).thenReturn(false);
        when(ruleRepository.save(any(AlertRule.class))).thenReturn(testRule);

        // when
        AlertRuleResponse result = alertRuleService.updateRule("test-rule", request);

        // then
        assertThat(result).isNotNull();
        assertThat(testRule.getRuleKey()).isEqualTo("new-key");
        verify(ruleCacheService).cacheRule(testRule);
    }

    @Test
    void batchCreateRules_multipleRequests_createsRules() throws JsonProcessingException {
        // given
        CreateAlertRuleRequest request1 = new CreateAlertRuleRequest();
        request1.setRuleKey("rule-1");
        request1.setRuleName("Rule 1");
        request1.setRuleType("SPEED");
        request1.setParameters("{\"speedLimit\":80.0}");
        request1.setEnabled(true);

        CreateAlertRuleRequest request2 = new CreateAlertRuleRequest();
        request2.setRuleKey("rule-2");
        request2.setRuleName("Rule 2");
        request2.setRuleType("TIME");
        request2.setParameters("{\"maxIdleMinutes\":30}");
        request2.setEnabled(true);

        when(ruleRepository.existsByRuleKeyAndOrganizationId(anyString(), eq(1L))).thenReturn(false);
        when(ruleRepository.save(any(AlertRule.class))).thenReturn(testRule);
        when(objectMapper.readValue(anyString(), any(com.fasterxml.jackson.core.type.TypeReference.class)))
            .thenAnswer(inv -> {
                String json = inv.getArgument(0, String.class);
                if (json.contains("speedLimit")) {
                    return Map.of("speedLimit", 80.0);
                } else if (json.contains("maxIdleMinutes")) {
                    return Map.of("maxIdleMinutes", 30);
                }
                return Collections.emptyMap();
            });

        // when
        List<AlertRuleResponse> results = alertRuleService.batchCreateRules(List.of(request1, request2));

        // then
        assertThat(results).hasSize(2);
        verify(ruleRepository, times(2)).save(any(AlertRule.class));
    }

    @Test
    void batchEnableRules_multipleRules_enablesThem() {
        // given
        Set<String> ruleKeys = Set.of("rule-1", "rule-2");
        AlertRule disabledRule1 = AlertRuleTestBuilder.defaultRule()
            .ruleKey("rule-1")
            .enabled(false)
            .build();
        AlertRule disabledRule2 = AlertRuleTestBuilder.defaultRule()
            .ruleKey("rule-2")
            .enabled(false)
            .build();

        when(ruleRepository.findByRuleKeyAndOrganizationId("rule-1", 1L)).thenReturn(Optional.of(disabledRule1));
        when(ruleRepository.findByRuleKeyAndOrganizationId("rule-2", 1L)).thenReturn(Optional.of(disabledRule2));

        // when
        alertRuleService.batchEnableRules(new HashSet<>(ruleKeys));

        // then
        verify(ruleRepository, times(2)).save(any(AlertRule.class));
        verify(ruleCacheService, times(2)).cacheRule(any(AlertRule.class));
    }
}
