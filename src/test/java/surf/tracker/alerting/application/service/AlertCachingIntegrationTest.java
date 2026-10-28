package com.surf.tracker.alerting.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.surf.tracker.alerting.api.dto.*;
import com.surf.tracker.alerting.domain.TrackerAlertTestBuilder;
import com.surf.tracker.alerting.domain.entity.TrackerAlert;
import com.surf.tracker.alerting.domain.enums.AlertSeverity;
import com.surf.tracker.alerting.domain.enums.AlertType;
import com.surf.tracker.alerting.exception.AlertException;
import com.surf.tracker.alerting.infrastructure.repository.TrackerAlertRepository;
import com.surf.tracker.shared.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.support.AopUtils;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.data.domain.PageImpl;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AlertCachingIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-10-21T12:00:00Z");
    private AnnotationConfigApplicationContext context;
    private TrackerAlertRepository repository;
    private AlertService alerts;
    private AlertService.AlertQueryService queries;
    private TrackerAlert first;
    private TrackerAlert second;

    @BeforeEach
    void setUp() {
        repository = mock(TrackerAlertRepository.class);
        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager();
        cacheManager.setAllowNullValues(false);
        context = new AnnotationConfigApplicationContext();
        context.register(TenantCachingTestConfiguration.class);
        context.registerBean(CacheManager.class, () -> cacheManager);
        context.registerBean(ObjectMapper.class, () -> new ObjectMapper().findAndRegisterModules());
        context.registerBean(Clock.class, () -> Clock.fixed(NOW, ZoneOffset.UTC));
        context.registerBean(TrackerAlertRepository.class, () -> repository);
        context.registerBean(AlertService.AlertQueryService.class);
        context.registerBean(AlertService.class);
        context.refresh();
        alerts = context.getBean(AlertService.class);
        queries = context.getBean(AlertService.AlertQueryService.class);
        assertThat(AopUtils.isAopProxy(alerts)).isTrue();
        first = alert(1L, 1L, "First tenant");
        second = alert(2L, 2L, "Second tenant");
        when(repository.findById(1L)).thenReturn(Optional.of(first)); // Expose the unsafe legacy lookup.
        when(repository.findByIdAndOrganizationId(1L, 1L)).thenReturn(Optional.of(first));
        when(repository.findByIdAndOrganizationId(1L, 2L)).thenReturn(Optional.empty());
        when(repository.save(any(TrackerAlert.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.findActiveVehicleAlerts("same-vehicle", 1L)).thenReturn(List.of(first));
        when(repository.findActiveVehicleAlerts("same-vehicle", 2L)).thenReturn(List.of(second));
        when(repository.findActiveAlerts(eq(1L), any())).thenReturn(new PageImpl<>(List.of(first)));
        when(repository.findActiveAlerts(eq(2L), any())).thenReturn(new PageImpl<>(List.of(second)));
        when(repository.findVehicleAlertsInRange(eq("same-vehicle"), eq(1L), any(), any())).thenReturn(List.of(first));
        when(repository.findVehicleAlertsInRange(eq("same-vehicle"), eq(2L), any(), any())).thenReturn(List.of(second));
        when(repository.searchAlerts(eq(1L), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(new PageImpl<>(List.of(first)));
        when(repository.searchAlerts(eq(2L), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(new PageImpl<>(List.of(second)));
        TenantContext.setCurrentOrgId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        if (context != null) context.close();
    }

    @Test
    void getAlertById_foreignIdOnColdCache_isNotFound() {
        // given
        TenantContext.setCurrentOrgId(2L);

        // when / then
        assertThatThrownBy(() -> alerts.getAlertById(1L)).isInstanceOf(AlertException.class);
        verify(repository, never()).findById(anyLong());
    }

    @Test
    void getAlertById_foreignIdAlreadyCached_isNotFound() {
        // given
        assertThat(alerts.getAlertById(1L).getMessage()).isEqualTo("First tenant");
        TenantContext.setCurrentOrgId(2L);

        // when / then
        assertThatThrownBy(() -> alerts.getAlertById(1L)).isInstanceOf(AlertException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"acknowledge", "resolve"})
    void mutateAlert_detailResponseAlreadyCached_usesScopedEntityLookup(String mutation) {
        // given
        alerts.getAlertById(1L);

        // when
        AlertResponse result;
        if (mutation.equals("acknowledge")) {
            AcknowledgeAlertRequest request = new AcknowledgeAlertRequest();
            request.setAcknowledgedBy("operator");
            result = alerts.acknowledgeAlert(1L, request);
            assertThat(result.getAcknowledged()).isTrue();
        } else {
            ResolveAlertRequest request = new ResolveAlertRequest();
            request.setResolvedBy("operator");
            result = alerts.resolveAlert(1L, request);
            assertThat(result.getResolved()).isTrue();
        }

        // then
        verify(repository).save(first);
        verify(repository, never()).findById(anyLong());
    }

    @ParameterizedTest
    @ValueSource(strings = {"active", "paged", "search", "recent"})
    void readAlerts_sameQueryAcrossTenants_isolatesResults(String query) {
        // given
        read(query);

        // when
        TenantContext.setCurrentOrgId(2L);
        var result = read(query);

        // then
        assertThat(result).extracting(AlertResponse::getMessage).containsExactly("Second tenant");
    }

    @Test
    void searchAlerts_equivalentRequests_reuseCurrentTenantEntry() {
        // given / when
        alerts.searchAlerts(new AlertSearchRequest());
        alerts.searchAlerts(new AlertSearchRequest());

        // then
        verify(repository, times(1)).searchAlerts(eq(1L), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void searchAlerts_mutatedRequest_usesNewFilterKey() {
        // given
        AlertSearchRequest request = new AlertSearchRequest();
        request.setSeverity(AlertSeverity.WARNING);
        alerts.searchAlerts(request);
        when(repository.searchAlerts(eq(1L), any(), any(), any(), eq(AlertSeverity.CRITICAL), any(), any(), any(), any(), any(), any()))
            .thenReturn(new PageImpl<>(List.of()));

        // when
        request.setSeverity(AlertSeverity.CRITICAL);
        var result = alerts.searchAlerts(request);

        // then
        assertThat(result.getContent()).isEmpty();
    }

    @Test
    void getUnacknowledgedCountBySeverity_sameQueryAcrossTenants_isolatesStatistics() {
        // given
        when(repository.countUnacknowledgedBySeverity(any(), eq(1L))).thenReturn(1L);
        when(repository.countUnacknowledgedBySeverity(any(), eq(2L))).thenReturn(2L);
        alerts.getUnacknowledgedCountBySeverity();

        // when
        TenantContext.setCurrentOrgId(2L);

        // then
        assertThat(alerts.getUnacknowledgedCountBySeverity()).containsEntry("WARNING", 2L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"acknowledge", "resolve"})
    void bulkMutation_mixedTenantIds_neverLoadsUnscopedEntities(String mutation) {
        // given
        when(repository.findAllById(any())).thenReturn(List.of(first, second));
        when(repository.findByIds(List.of(1L, 2L), 1L)).thenReturn(List.of(first));

        // when
        if (mutation.equals("acknowledge")) alerts.bulkAcknowledgeAlerts(List.of(1L, 2L), "operator");
        else alerts.bulkResolveAlerts(List.of(1L, 2L), "operator", "resolved");

        // then
        assertThat(second.getAcknowledged()).isFalse();
        assertThat(second.getResolved()).isFalse();
        verify(repository).saveAll(List.of(first));
        verify(repository, never()).findAllById(any());
    }

    @Test
    void updateAlert_warmOtherTenantCaches_invalidatesOnlyWriter() {
        // given
        read("active");
        TenantContext.setCurrentOrgId(2L);
        read("active");
        TenantContext.setCurrentOrgId(1L);
        UpdateAlertRequest update = new UpdateAlertRequest();
        update.setMessage("Changed");

        // when
        alerts.updateAlert(1L, update);

        // then
        assertThat(read("active")).extracting(AlertResponse::getMessage).containsExactly("Changed");
        TenantContext.setCurrentOrgId(2L);
        assertThat(read("active")).extracting(AlertResponse::getMessage).containsExactly("Second tenant");
        verify(repository, times(1)).findActiveVehicleAlerts("same-vehicle", 2L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"create", "acknowledge", "resolve", "update", "delete", "bulkAcknowledge", "bulkResolve", "cleanup", "automated"})
    void mutateAlert_warmQueries_invalidatesAllShapesAndStatistics(String mutation) {
        // given
        read("active"); read("paged"); read("search"); read("recent");
        alerts.getUnacknowledgedCountBySeverity();
        when(repository.findByIds(anyList(), eq(1L))).thenReturn(List.of(first));
        when(repository.findStaleAlerts(any(), eq(1L))).thenReturn(List.of(first));

        // when
        switch (mutation) {
            case "create" -> alerts.createAlert(new CreateAlertRequest());
            case "acknowledge" -> {
                AcknowledgeAlertRequest request = new AcknowledgeAlertRequest();
                request.setAcknowledgedBy("operator"); alerts.acknowledgeAlert(1L, request);
            }
            case "resolve" -> {
                ResolveAlertRequest request = new ResolveAlertRequest();
                request.setResolvedBy("operator"); alerts.resolveAlert(1L, request);
            }
            case "update" -> {
                UpdateAlertRequest request = new UpdateAlertRequest();
                request.setMessage("Changed"); alerts.updateAlert(1L, request);
            }
            case "delete" -> alerts.deleteAlert(1L);
            case "bulkAcknowledge" -> alerts.bulkAcknowledgeAlerts(List.of(1L), "operator");
            case "bulkResolve" -> alerts.bulkResolveAlerts(List.of(1L), "operator", "resolved");
            case "cleanup" -> alerts.cleanupStaleAlerts(NOW);
            case "automated" -> alerts.processAutomatedAlert("same-vehicle", "tracker", AlertType.OVERSPEED,
                AlertSeverity.WARNING, "Automated", Map.of());
            default -> throw new AssertionError("Unexpected mutation");
        }
        first.setMessage("Changed");

        // then: query snapshots must be rebuilt, independently of repository mutation semantics.
        for (String query : List.of("active", "paged", "search", "recent")) {
            assertThat(read(query)).extracting(AlertResponse::getMessage).containsExactly("Changed");
        }
        alerts.getUnacknowledgedCountBySeverity();
        verify(repository, times(2)).countUnacknowledgedBySeverity(AlertSeverity.WARNING, 1L);
    }

    @Test
    void getAlertStatistics_sameDatesAcrossTenants_isolatesResults() {
        // given
        when(repository.getAlertTypeStatistics(1L, NOW)).thenReturn(
            java.util.Collections.singletonList(new Object[]{"OVERSPEED", 1L}));
        when(repository.getAlertTypeStatistics(2L, NOW)).thenReturn(
            java.util.Collections.singletonList(new Object[]{"OVERSPEED", 2L}));
        queries.getAlertStatistics(NOW, NOW);

        // when
        TenantContext.setCurrentOrgId(2L);

        // then
        assertThat(queries.getAlertStatistics(NOW, NOW)).containsEntry("OVERSPEED", 2L);
    }

    @Test
    void bulkAcknowledgeAlerts_moreThanOneBatch_scopesEveryBatch() {
        // given
        List<Long> ids = java.util.stream.LongStream.rangeClosed(1, 101).boxed().toList();
        List<List<Long>> batches = new java.util.ArrayList<>();
        when(repository.findByIds(anyList(), eq(1L))).thenAnswer(invocation -> {
            batches.add(List.copyOf(invocation.getArgument(0)));
            return batches.size() == 1 ? List.of(first) : List.of();
        });

        // when
        alerts.bulkAcknowledgeAlerts(ids, "operator");

        // then
        assertThat(batches).containsExactly(ids.subList(0, 100), ids.subList(100, 101));
        verify(repository, times(2)).findByIds(anyList(), eq(1L));
        verify(repository).saveAll(List.of(first));
        verify(repository, never()).findAllById(any());
    }

    @Test
    void createAlert_missingTenant_rejectsBeforeSave() {
        // given
        TenantContext.clear();

        // when / then
        assertThatThrownBy(() -> alerts.createAlert(new CreateAlertRequest()))
            .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void readAlert_missingTenant_rejectsBeforeRepositoryAccess() {
        // given
        TenantContext.clear();

        // when / then
        assertThatThrownBy(() -> alerts.getAlertById(1L)).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(repository);
    }

    private List<AlertResponse> read(String query) {
        return switch (query) {
            case "active" -> alerts.getActiveAlerts("same-vehicle");
            case "paged" -> alerts.getActiveAlertsPaged(0, 20).getContent();
            case "search" -> alerts.searchAlerts(new AlertSearchRequest()).getContent();
            case "recent" -> alerts.getRecentAlerts("same-vehicle", 10);
            default -> throw new AssertionError("Unexpected query");
        };
    }

    private TrackerAlert alert(Long id, Long organizationId, String message) {
        TrackerAlert alert = TrackerAlertTestBuilder.defaultAlert().alertId(id).vehicleId("same-vehicle")
            .message(message).build();
        alert.setOrganizationId(organizationId);
        alert.setTriggeredAt(NOW); alert.setCreatedAt(NOW); alert.setUpdatedAt(NOW);
        return alert;
    }
}
