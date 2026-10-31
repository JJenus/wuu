package com.surf.tracker.alerting.application.service;


import com.surf.tracker.alerting.api.dto.GeofencePointDto;
import com.surf.tracker.alerting.api.dto.GeofenceResponse;
import com.surf.tracker.alerting.api.dto.PagedResponse;
import com.surf.tracker.alerting.api.dto.SearchRequest;
import com.surf.tracker.alerting.domain.entity.Geofence;
import com.surf.tracker.alerting.exception.AlertException;
import com.surf.tracker.alerting.infrastructure.cache.GeofenceCacheService;
import com.surf.tracker.alerting.infrastructure.repository.GeofenceRepository;
import com.surf.tracker.shared.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;

import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@CacheConfig(cacheResolver = "tenantAlertRuleCacheResolver", keyGenerator = "alertCacheKeyGenerator")
public class GeofenceService {

    private static final Logger logger = LoggerFactory.getLogger(GeofenceService.class);

    private final GeofenceRepository geofenceRepository;
    private final GeofenceCacheService geofenceCacheService;

    public GeofenceService(
            GeofenceRepository geofenceRepository,
            GeofenceCacheService geofenceCacheService) {
        this.geofenceRepository = geofenceRepository;
        this.geofenceCacheService = geofenceCacheService;
    }

    @Transactional
    @CacheEvict(value = "geofencesPaged", allEntries = true)
    public Geofence createGeofence(Geofence geofence) {
        geofence.setOrganizationId(requireOrganizationId());
        Geofence saved = geofenceRepository.save(geofence);

        // Cache the geofence
        geofenceCacheService.cacheGeofence(saved);

        logger.info("Created geofence: {} for {} vehicles",
                saved.getName(), saved.getVehicleIds().size());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Geofence> getVehicleGeofences(String vehicleId) {
        Long orgId = requireOrganizationId();
        // Try cache first
        List<Geofence> cached = geofenceCacheService.getVehicleGeofences(vehicleId);
        if (cached != null) {
            logger.debug("Cache hit for vehicle geofences: {}", vehicleId);
            return cached;
        }

        // Cache miss - load from DB
        List<Geofence> geofences = geofenceRepository.findByVehicleId(vehicleId, orgId);

        // Cache the result
        geofenceCacheService.cacheVehicleGeofences(vehicleId, geofences);

        return geofences;
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "geofencesPaged")
    public PagedResponse<GeofenceResponse> getVehicleGeofencesPaged(String vehicleId, SearchRequest searchRequest) {
        Pageable pageable = createPageable(searchRequest);
        Page<Geofence> page = geofenceRepository.findByVehicleId(vehicleId, TenantContext.getCurrentOrgId(), pageable);

        PagedResponse<GeofenceResponse> response = new PagedResponse<>(page.map(this::toResponse));

        return response;
    }

    @Transactional(readOnly = true)
    public List<Geofence> getActiveGeofences(String vehicleId) {
        return geofenceRepository.findByVehicleIdAndIsActive(vehicleId, requireOrganizationId());
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "geofencesPaged")
    public PagedResponse<GeofenceResponse> getActiveGeofencesPaged(String vehicleId, SearchRequest searchRequest) {
        Pageable pageable = createPageable(searchRequest);
        Page<Geofence> page = geofenceRepository.findByVehicleIdAndIsActive(vehicleId, TenantContext.getCurrentOrgId(), pageable);

        PagedResponse<GeofenceResponse> response = new PagedResponse<>(page.map(this::toResponse));

        return response;
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "geofencesPaged")
    public PagedResponse<GeofenceResponse> searchGeofences(SearchRequest searchRequest) {
        Pageable pageable = createPageable(searchRequest);
        Page<Geofence> page = geofenceRepository.searchGeofences(
                searchRequest.getVehicleId(),
                searchRequest.getSearch(),
                searchRequest.getActive(),
                TenantContext.getCurrentOrgId(),
                pageable);

        PagedResponse<GeofenceResponse> response = new PagedResponse<>(page.map(this::toResponse));

        return response;
    }

    @Transactional(readOnly = true)
    public Geofence getGeofenceById(Long geofenceId) {
        Long orgId = requireOrganizationId();
        // Try cache first
        return geofenceCacheService.getGeofenceById(geofenceId)
                .filter(g -> orgId.equals(g.getOrganizationId()))
                .orElseGet(() -> {
                    // Cache miss - load from DB with org scoping
                    Geofence geofence = geofenceRepository.findByIdAndOrganizationId(geofenceId, orgId)
                            .orElseThrow(() -> AlertException.geofenceNotFound(String.valueOf(geofenceId)));

                    // Cache for future requests
                    geofenceCacheService.cacheGeofence(geofence);

                    return geofence;
                });
    }

    @Transactional
    @CacheEvict(value = "geofencesPaged", allEntries = true)
    public Geofence updateGeofence(Long geofenceId, Geofence updates) {
        Long orgId = requireOrganizationId();
        Geofence geofence = geofenceRepository.findByIdAndOrganizationId(geofenceId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Geofence not found"));

        // Track affected vehicles for cache invalidation
        Set<String> oldVehicleIds = new HashSet<>(geofence.getVehicleIds());
        Set<String> newVehicleIds = updates.getVehicleIds() != null ?
                updates.getVehicleIds() : oldVehicleIds;

        // Update fields
        if (updates.getName() != null) geofence.setName(updates.getName());
        if (updates.getDescription() != null) geofence.setDescription(updates.getDescription());
        if (updates.getIsActive() != null) geofence.setIsActive(updates.getIsActive());
        if (updates.getShapeType() != null) geofence.setShapeType(updates.getShapeType());

        // Update geometry if changed
        if (updates.getCenterLatitude() != null) {
            geofence.setCenterLatitude(updates.getCenterLatitude());
        }

        if (updates.getCenterLongitude() != null) {
            geofence.setCenterLongitude(updates.getCenterLongitude());
        }

        if (updates.getRadiusMeters() != null) {
            geofence.setRadiusMeters(updates.getRadiusMeters());
        }

        // Update vehicle associations
        if (updates.getVehicleIds() != null) {
            geofence.setVehicleIds(updates.getVehicleIds());
        }

        Geofence updated = geofenceRepository.save(geofence);

        // Update cache
        geofenceCacheService.cacheGeofence(updated);

        // Invalidate caches for all affected vehicles
        Set<String> allAffected = new HashSet<>(oldVehicleIds);
        allAffected.addAll(newVehicleIds);
        geofenceCacheService.invalidateVehicleGeofenceCaches(allAffected);

        logger.info("Updated geofence: {} affecting {} vehicles",
                geofenceId, allAffected.size());
        return updated;
    }

    @Transactional
    @CacheEvict(value = "geofencesPaged", allEntries = true)
    public void deleteGeofence(Long geofenceId) {
        Long orgId = requireOrganizationId();
        Geofence geofence = geofenceRepository.findByIdAndOrganizationId(geofenceId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Geofence not found"));

        Set<String> affectedVehicles = geofence.getVehicleIds();
        geofenceRepository.deleteById(geofenceId);

        // Invalidate caches
        geofenceCacheService.invalidateGeofence(geofenceId);
        geofenceCacheService.invalidateVehicleGeofenceCaches(affectedVehicles);

        logger.info("Deleted geofence: {} affecting {} vehicles",
                geofenceId, affectedVehicles.size());
    }

    @Transactional(readOnly = true)
    public List<Geofence> findNearbyGeofencesForVehicle(String vehicleId,
                                                        Double latitude,
                                                        Double longitude) {
        return geofenceRepository.findNearbyGeofencesForVehicle(vehicleId, latitude, longitude,
                requireOrganizationId());
    }

    public void checkGeofenceViolations(String vehicleId, Double latitude, Double longitude) {
        List<Geofence> nearbyGeofences = findNearbyGeofencesForVehicle(vehicleId, latitude, longitude);

        for (Geofence geofence : nearbyGeofences) {
            boolean isInside = geofence.isPointInside(latitude, longitude);
            logger.debug("Vehicle {} is {} geofence {} at [{}, {}]",
                    vehicleId,
                    isInside ? "inside" : "outside",
                    geofence.getName(),
                    latitude, longitude);
        }
    }

    @Transactional(readOnly = true)
    public boolean existsAndActive(Long geofenceId) {
        Long orgId = requireOrganizationId();
        return geofenceRepository.findByIdAndOrganizationId(geofenceId, orgId)
                .map(g -> Boolean.TRUE.equals(g.getIsActive()))
                .orElse(false);
    }

    // ========== HELPER METHODS ==========

    private Pageable createPageable(SearchRequest searchRequest) {
        Sort sort = Sort.by(searchRequest.getSortDirection(), searchRequest.getSortBy());
        return PageRequest.of(searchRequest.getPage(), searchRequest.getSize(), sort);
    }

    private Long requireOrganizationId() {
        Long organizationId = TenantContext.getCurrentOrgId();
        if (organizationId == null || organizationId <= 0) {
            throw new IllegalStateException("Organization context is required for geofences");
        }
        return organizationId;
    }

    private GeofenceResponse toResponse(Geofence geofence) {
        GeofenceResponse response = new GeofenceResponse();
        response.setGeofenceId(geofence.getGeofenceId());
        response.setVehicleId(geofence.getVehicleIds().stream().findFirst().orElse(null));
        response.setName(geofence.getName());
        response.setDescription(geofence.getDescription());
        response.setShapeType(geofence.getShapeType());
        response.setCenterLatitude(geofence.getCenterLatitude());
        response.setCenterLongitude(geofence.getCenterLongitude());
        response.setRadiusMeters(geofence.getRadiusMeters());
        response.setActive(Boolean.TRUE.equals(geofence.getIsActive()));
        response.setCreatedAt(geofence.getCreatedAt());
        response.setUpdatedAt(geofence.getUpdatedAt());

        // Convert points
        if (geofence.getPoints() != null && !geofence.getPoints().isEmpty()) {
            response.setPoints(geofence.getPoints().stream()
                    .map(point -> {
                        GeofencePointDto dto =
                                new GeofencePointDto();
                        dto.setLatitude(point.getLatitude());
                        dto.setLongitude(point.getLongitude());
                        return dto;
                    })
                    .collect(Collectors.toList()));
        }

        return response;
    }
}
