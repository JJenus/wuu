package com.jjenus.tracker.alerting.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjenus.tracker.alerting.api.dto.GeofenceResponse;
import com.jjenus.tracker.alerting.api.dto.SearchRequest;
import com.jjenus.tracker.alerting.domain.GeofenceTestBuilder;
import com.jjenus.tracker.alerting.domain.entity.Geofence;
import com.jjenus.tracker.alerting.infrastructure.cache.GeofenceCacheService;
import com.jjenus.tracker.alerting.infrastructure.cache.RedisKeyGenerator;
import com.jjenus.tracker.alerting.infrastructure.repository.GeofenceRepository;
import com.jjenus.tracker.alerting.support.RedisCacheTestStore;
import com.jjenus.tracker.shared.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.cache.CacheManager;
import org.springframework.cache.Cache;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GeofenceCachingIntegrationTest {
    private AnnotationConfigApplicationContext context;
    private GeofenceService geofences;
    private GeofenceRepository repository;
    private Geofence first;
    private Geofence second;
    private ConcurrentMapCacheManager cacheManager;

    @BeforeEach
    void setUp() {
        repository = mock(GeofenceRepository.class);
        RedisCacheTestStore store = new RedisCacheTestStore();
        context = new AnnotationConfigApplicationContext();
        context.register(TenantCachingTestConfiguration.class);
        cacheManager = spy(new ConcurrentMapCacheManager());
        context.registerBean(CacheManager.class, () -> cacheManager);
        context.registerBean(ObjectMapper.class, () -> new ObjectMapper().findAndRegisterModules());
        context.registerBean(RedisTemplate.class, store::template);
        context.registerBean(RedisKeyGenerator.class);
        context.registerBean(GeofenceRepository.class, () -> repository);
        context.registerBean(GeofenceCacheService.class);
        context.registerBean(GeofenceService.class);
        context.refresh();
        geofences = context.getBean(GeofenceService.class);
        first = geofence(1L, "First tenant");
        second = geofence(2L, "Second tenant");
        when(repository.findByVehicleId("same-vehicle", 1L)).thenReturn(List.of(first));
        when(repository.findByVehicleId("same-vehicle", 2L)).thenReturn(List.of(second));
        when(repository.findByVehicleId(eq("same-vehicle"), eq(1L), any())).thenReturn(new PageImpl<>(List.of(first)));
        when(repository.findByVehicleId(eq("same-vehicle"), eq(2L), any())).thenReturn(new PageImpl<>(List.of(second)));
        when(repository.findByVehicleIdAndIsActive(eq("same-vehicle"), eq(1L), any())).thenReturn(new PageImpl<>(List.of(first)));
        when(repository.findByVehicleIdAndIsActive(eq("same-vehicle"), eq(2L), any())).thenReturn(new PageImpl<>(List.of(second)));
        when(repository.searchGeofences(any(), any(), any(), eq(1L), any())).thenReturn(new PageImpl<>(List.of(first)));
        when(repository.searchGeofences(any(), any(), any(), eq(2L), any())).thenReturn(new PageImpl<>(List.of(second)));
        when(repository.findByIdAndOrganizationId(1L, 1L)).thenReturn(Optional.of(first));
        when(repository.save(any(Geofence.class))).thenAnswer(invocation -> invocation.getArgument(0));
        TenantContext.setCurrentOrgId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        if (context != null) context.close();
    }

    @Test
    void getVehicleGeofences_sameVehicleAcrossTenants_isolatesManualListCache() {
        // given
        geofences.getVehicleGeofences("same-vehicle");

        // when
        TenantContext.setCurrentOrgId(2L);
        var result = geofences.getVehicleGeofences("same-vehicle");

        // then
        assertThat(result).extracting(Geofence::getName).containsExactly("Second tenant");
        verify(repository).findByVehicleId("same-vehicle", 2L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"all", "active", "search"})
    void readPage_sameQueryAcrossTenants_isolatesResults(String query) {
        // given
        read(query);

        // when
        TenantContext.setCurrentOrgId(2L);

        // then
        assertThat(read(query)).extracting(GeofenceResponse::getName).containsExactly("Second tenant");
    }

    @Test
    void searchGeofences_equivalentRequests_reusesCurrentTenantCache() {
        // given / when
        read("search");
        read("search");

        // then
        verify(repository, times(1)).searchGeofences(any(), any(), any(), eq(1L), any());
    }

    @Test
    void searchGeofences_vehiclePageAlreadyCached_keepsDifferentQueryShapesSeparate() {
        // given: the vehicle endpoint does not apply the search filter, but the search endpoint does.
        SearchRequest request = new SearchRequest();
        request.setVehicleId("same-vehicle");
        request.setSearch("not-found");
        geofences.getVehicleGeofencesPaged("same-vehicle", request);
        when(repository.searchGeofences(eq("same-vehicle"), eq("not-found"), any(), eq(1L), any()))
            .thenReturn(new PageImpl<>(List.of()));

        // when / then
        assertThat(geofences.searchGeofences(request).getContent()).isEmpty();
    }

    @Test
    void searchGeofences_hashCollision_keepsDifferentSearchValuesSeparate() {
        // given
        SearchRequest a = new SearchRequest(); a.setSearch("Aa");
        SearchRequest b = new SearchRequest(); b.setSearch("BB");
        geofences.searchGeofences(a);
        when(repository.searchGeofences(any(), eq("BB"), any(), eq(1L), any()))
            .thenReturn(new PageImpl<>(List.of()));

        // when / then
        assertThat(geofences.searchGeofences(b).getContent()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"create", "update", "delete"})
    void mutateGeofence_warmPages_invalidatesOnlyWritingTenant(String mutation) {
        // given
        for (String query : List.of("all", "active", "search")) read(query);
        TenantContext.setCurrentOrgId(2L);
        for (String query : List.of("all", "active", "search")) read(query);
        TenantContext.setCurrentOrgId(1L);

        // when
        if (mutation.equals("create")) geofences.createGeofence(geofence(1L, "Created"));
        else if (mutation.equals("delete")) geofences.deleteGeofence(1L);
        else {
            Geofence update = new Geofence(); update.setName("Changed");
            geofences.updateGeofence(1L, update);
        }
        first.setName("Changed");

        // then
        for (String query : List.of("all", "active", "search")) {
            assertThat(read(query)).extracting(GeofenceResponse::getName).containsExactly("Changed");
        }
        TenantContext.setCurrentOrgId(2L);
        for (String query : List.of("all", "active", "search")) {
            assertThat(read(query)).extracting(GeofenceResponse::getName).containsExactly("Second tenant");
        }
        verify(repository, times(1)).searchGeofences(any(), any(), any(), eq(2L), any());
    }

    @Test
    void updateGeofence_vehicleReassignment_preservesNewAssociationsAndInvalidatesOldLists() {
        // given
        first.setVehicleIds(Set.of("same-vehicle")); // Read-only input must not be mutated during invalidation.
        geofences.getVehicleGeofences("same-vehicle");
        Geofence update = new Geofence(); update.setVehicleIds(Set.of("new-vehicle"));

        // when
        Geofence result = geofences.updateGeofence(1L, update);
        when(repository.findByVehicleId("same-vehicle", 1L)).thenReturn(List.of());

        // then
        assertThat(result.getVehicleIds()).containsExactly("new-vehicle");
        assertThat(geofences.getVehicleGeofences("same-vehicle")).isEmpty();
    }

    @Test
    void readAndMutateGeofence_redisUnavailable_preservesDatabaseFallback() {
        // given: old pagination caching was best-effort for reads, writes and invalidation.
        Cache failedCache = mock(Cache.class);
        when(failedCache.getName()).thenReturn("geofencesPaged:v2:org:1");
        when(cacheManager.getCache("geofencesPaged:v2:org:1")).thenReturn(failedCache);
        when(failedCache.get(any())).thenThrow(new IllegalStateException("Redis unavailable"));
        doThrow(new IllegalStateException("Redis unavailable")).when(failedCache).put(any(), any());
        doThrow(new IllegalStateException("Redis unavailable")).when(failedCache).clear();

        // when / then
        assertThat(read("all")).extracting(GeofenceResponse::getName).containsExactly("First tenant");
        Geofence update = new Geofence(); update.setName("Changed");
        assertThat(geofences.updateGeofence(1L, update).getName()).isEqualTo("Changed");
        verify(repository).save(first);
    }

    @Test
    void readGeofence_missingTenant_rejectsBeforeRepositoryAccess() {
        // given
        TenantContext.clear();

        // when / then
        assertThatThrownBy(() -> geofences.getVehicleGeofences("same-vehicle"))
            .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(repository);
    }

    private List<GeofenceResponse> read(String query) {
        SearchRequest request = new SearchRequest();
        request.setVehicleId("same-vehicle");
        return switch (query) {
            case "all" -> geofences.getVehicleGeofencesPaged("same-vehicle", request).getContent();
            case "active" -> geofences.getActiveGeofencesPaged("same-vehicle", request).getContent();
            case "search" -> geofences.searchGeofences(request).getContent();
            default -> throw new AssertionError("Unexpected query");
        };
    }

    private Geofence geofence(Long organizationId, String name) {
        Geofence geofence = GeofenceTestBuilder.defaultGeofence().geofenceId(organizationId).name(name)
            .vehicleId("same-vehicle").build();
        geofence.setOrganizationId(organizationId);
        geofence.setCreatedAt(Instant.parse("2026-10-21T12:00:00Z"));
        geofence.setUpdatedAt(geofence.getCreatedAt());
        return geofence;
    }
}
