package com.jjenus.tracker.alerting.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjenus.tracker.alerting.api.dto.*;
import com.jjenus.tracker.alerting.domain.entity.AlertRule;
import com.jjenus.tracker.alerting.domain.entity.Geofence;
import com.jjenus.tracker.alerting.domain.enums.AlertRuleType;
import com.jjenus.tracker.alerting.domain.parameters.GeofenceRuleParameters;
import com.jjenus.tracker.alerting.domain.parameters.IdleRuleParameters;
import com.jjenus.tracker.alerting.domain.parameters.RuleParameters;
import com.jjenus.tracker.alerting.domain.parameters.RuleParametersMapper;
import com.jjenus.tracker.alerting.domain.parameters.SpeedRuleParameters;
import com.jjenus.tracker.alerting.exception.AlertException;
import com.jjenus.tracker.alerting.infrastructure.cache.AlertRuleCacheService;
import com.jjenus.tracker.alerting.infrastructure.cache.RedisKeyGenerator;
import com.jjenus.tracker.alerting.infrastructure.cache.VehicleRuleCacheService;
import com.jjenus.tracker.alerting.infrastructure.cache.TransactionalCacheInvalidation;
import com.jjenus.tracker.alerting.infrastructure.repository.AlertRuleRepository;
import com.jjenus.tracker.shared.redis.RedisKeyScanner;
import com.jjenus.tracker.shared.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service
@Transactional
@CacheConfig(cacheResolver = "tenantAlertRuleCacheResolver", keyGenerator = "alertRuleCacheKeyGenerator")
public class AlertRuleService {

    private static final Logger logger = LoggerFactory.getLogger(AlertRuleService.class);

    private final AlertRuleRepository ruleRepository;
    private final AlertRuleCacheService ruleCacheService;
    private final VehicleRuleCacheService vehicleRuleCacheService;
    private final GeofenceRuleValidator geofenceRuleValidator;
    private final ObjectMapper objectMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisKeyGenerator keyGenerator;
    private final RuleParametersMapper ruleParametersMapper;
    private final Clock clock;

    public AlertRuleService(
            AlertRuleRepository ruleRepository,
            AlertRuleCacheService ruleCacheService,
            VehicleRuleCacheService vehicleRuleCacheService,
            GeofenceRuleValidator geofenceRuleValidator,
            ObjectMapper objectMapper,
            RedisTemplate<String, Object> redisTemplate,
            RedisKeyGenerator keyGenerator,
            RuleParametersMapper ruleParametersMapper,
            Clock clock) {
        this.ruleRepository = ruleRepository;
        this.ruleCacheService = ruleCacheService;
        this.vehicleRuleCacheService = vehicleRuleCacheService;
        this.geofenceRuleValidator = geofenceRuleValidator;
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
        this.keyGenerator = keyGenerator;
        this.ruleParametersMapper = ruleParametersMapper;
        this.clock = clock;
    }

    // ========== TEMPLATE METHODS ==========

    @Transactional
    @CacheEvict(value = {"alertRules", "alertRulesPaged", "vehicleRules"}, allEntries = true)
    public AlertRuleResponse createOverspeedRule(OverspeedRuleTemplateRequest request) {
        logger.info("Creating overspeed rule: {}", request.getRuleKey());

        validateRuleKeyUniqueness(request.getRuleKey());
        validateOverspeedRuleRequest(request);

        // Build parameters with validation
        RuleParameters parameters = buildOverspeedParameters(request);

        // Build actions configuration
        String actionsJson = buildOverspeedActions();

        // Create and save rule
        AlertRule rule = createBaseRule(request, AlertRuleType.SPEED, parameters);
        rule.setActions(actionsJson);
        rule.setVehicleIds(request.getVehicleIds());

        AlertRule saved = saveAndCacheRule(rule);

        logger.info("Overspeed rule created successfully: {} for {} vehicles",
                saved.getRuleKey(), request.getVehicleIds().size());

        return toResponse(saved);
    }

    @Transactional
    @CacheEvict(value = {"alertRules", "alertRulesPaged", "vehicleRules"}, allEntries = true)
    public AlertRuleResponse createIdleTimeoutRule(IdleTimeoutRuleTemplateRequest request) {
        logger.info("Creating idle timeout rule: {}", request.getRuleKey());

        validateRuleKeyUniqueness(request.getRuleKey());
        validateIdleTimeoutRuleRequest(request);

        // Build parameters
        RuleParameters parameters = buildIdleTimeoutParameters(request);

        // Create and save rule
        AlertRule rule = createBaseRule(request, AlertRuleType.TIME, parameters);
        rule.setVehicleIds(request.getVehicleIds());

        AlertRule saved = saveAndCacheRule(rule);

        logger.info("Idle timeout rule created successfully: {} for {} vehicles",
                saved.getRuleKey(), request.getVehicleIds().size());

        return toResponse(saved);
    }

    @Transactional
    @CacheEvict(value = {"alertRules", "alertRulesPaged", "vehicleRules"}, allEntries = true)
    public AlertRuleResponse createGeofenceRule(GeofenceRuleTemplateRequest request) {
        logger.info("Creating geofence rule: {}", request.getRuleKey());

        validateRuleKeyUniqueness(request.getRuleKey());

        // Validate geofence rule request
        geofenceRuleValidator.validateGeofenceRuleRequest(request);

        // Get validated geofence
        Geofence geofence = geofenceRuleValidator.getValidatedGeofence(request.getGeofenceId());

        // Build parameters with geofence details
        RuleParameters parameters = buildGeofenceParameters(request, geofence);

        // Validate vehicle associations
        validateVehicleGeofenceAssociation(geofence, request.getVehicleIds());

        // Create and save rule
        AlertRule rule = createBaseRule(request, AlertRuleType.GEOFENCE, parameters);
        rule.setVehicleIds(request.getVehicleIds());

        AlertRule saved = saveAndCacheRule(rule);

        logger.info("Geofence rule created successfully: {} for geofence {} and {} vehicles",
                saved.getRuleKey(), request.getGeofenceId(), request.getVehicleIds().size());

        return toResponse(saved);
    }

    // ========== CRUD METHODS ==========

    @Transactional
    @CacheEvict(value = {"alertRules", "alertRulesPaged", "vehicleRules"}, allEntries = true)
    public AlertRuleResponse createRule(CreateAlertRuleRequest request) {
        logger.info("Creating custom alert rule: {}", request.getRuleKey());

        validateRuleKeyUniqueness(request.getRuleKey());

        // Parse and validate rule type
        AlertRuleType ruleType = parseRuleType(request.getRuleType());

        // Parse and validate parameters
        Map<String, Object> parameters = parseAndValidateParameters(request.getParameters(), ruleType);

        // Extract vehicle IDs from parameters
        Set<String> vehicleIds = extractVehicleIdsFromParameters(parameters);

        // Create entity
        AlertRule rule = new AlertRule();
        rule.setRuleKey(request.getRuleKey());
        rule.setRuleName(request.getRuleName());
        rule.setRuleType(ruleType);
        rule.setParameters(parameters);
        rule.setPriority(request.getPriority());
        rule.setIsEnabled(request.isEnabled());
        rule.setVehicleIds(vehicleIds);
        rule.setCooldownMinutes(5);
        rule.setOrganizationId(TenantContext.getCurrentOrgId());

        AlertRule saved = saveAndCacheRule(rule);

        logger.info("Custom alert rule created successfully: {}", saved.getRuleKey());

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    @Cacheable("alertRules")
    public List<AlertRuleResponse> getAllRules() {
        List<AlertRuleResponse> result = new ArrayList<>();
        findAllBatched(PageRequest.of(0, 500), rule -> result.add(toResponse(rule)));
        return result;
    }

    private void findAllBatched(Pageable pageable, Consumer<AlertRule> consumer) {
        Long orgId = TenantContext.getCurrentOrgId();
        Page<AlertRule> page;
        do {
            page = ruleRepository.findByOrganizationId(orgId, pageable);
            page.forEach(consumer);
            pageable = PageRequest.of(pageable.getPageNumber() + 1, pageable.getPageSize());
        } while (page.hasNext());
    }

    @Transactional(readOnly = true)
    @Cacheable("alertRulesPaged")
    public PagedResponse<AlertRuleResponse> getAllRulesPaged(SearchRequest searchRequest) {
        String cacheKey = keyGenerator.getPaginatedRulesKey(
                searchRequest.getPage(),
                searchRequest.getSize(),
                searchRequest.getSortBy(),
                searchRequest.getSortDirection().name(),
                searchRequest.getSearch(),
                searchRequest.getRuleType() != null ? searchRequest.getRuleType().name() : null,
                searchRequest.getEnabled()
        );

        try {
            // Try cache first
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null && cached instanceof PagedResponse) {
                logger.debug("Cache hit for paginated rules");
                return (PagedResponse<AlertRuleResponse>) cached;
            }
        } catch (Exception e) {
            logger.warn("Failed to get paginated rules from cache", e);
        }

        // Cache miss - query database
        Pageable pageable = createPageable(searchRequest);
        Page<AlertRule> page = ruleRepository.searchAlertRules(
                TenantContext.getCurrentOrgId(),
                searchRequest.getSearch(),
                searchRequest.getRuleType(),
                searchRequest.getEnabled(),
                pageable);

        PagedResponse<AlertRuleResponse> response = new PagedResponse<>(page.map(this::toResponse));

        // Cache the result
        try {
            if (!TransactionalCacheInvalidation.defer(redisTemplate, keyGenerator.getAllRuleKeysPattern())) {
                redisTemplate.opsForValue().set(cacheKey, response,
                        RedisKeyGenerator.PAGINATION_CACHE_TTL, TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            logger.warn("Failed to cache paginated rules", e);
        }

        return response;
    }

    @Transactional(readOnly = true)
    @Cacheable("alertRules")
    public List<AlertRuleResponse> getEnabledRules() {
        return ruleRepository.findByIsEnabledAndOrganizationId(true, TenantContext.getCurrentOrgId()).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    @Cacheable("alertRulesPaged")
    public PagedResponse<AlertRuleResponse> getEnabledRulesPaged(SearchRequest searchRequest) {
        searchRequest.setEnabled(true); // Force enabled=true
        return getAllRulesPaged(searchRequest);
    }

    @Transactional(readOnly = true)
    @Cacheable("alertRules")
    public AlertRuleResponse getRuleByKey(String ruleKey) {
        // Try cache first
        var cachedRule = ruleCacheService.getRuleByKey(ruleKey);
        if (cachedRule.isPresent()) {
            logger.debug("Cache hit for rule: {}", ruleKey);
            return toResponse(cachedRule.get());
        }

        // Cache miss - load from DB
        AlertRule rule = ruleRepository.findByRuleKeyAndOrganizationId(ruleKey, TenantContext.getCurrentOrgId())
                .orElseThrow(() -> AlertException.ruleNotFound(ruleKey));

        // Cache the rule for future requests
        ruleCacheService.cacheRule(rule);

        return toResponse(rule);
    }

    @Transactional
    @CacheEvict(value = {"alertRules", "alertRulesPaged", "vehicleRules"}, allEntries = true)
    public AlertRuleResponse updateRule(String ruleKey, UpdateAlertRuleRequest request) {
        logger.info("Updating alert rule: {}", ruleKey);

        AlertRule rule = ruleRepository.findByRuleKeyAndOrganizationId(ruleKey, TenantContext.getCurrentOrgId())
                .orElseThrow(() -> AlertException.ruleNotFound(ruleKey));

        boolean hasChanges = false;
        Set<String> oldVehicleIds = new HashSet<>(rule.getVehicleIds());

        // Update fields if provided
        if (StringUtils.hasText(request.getRuleName())) {
            rule.setRuleName(request.getRuleName());
            hasChanges = true;
        }

        if (StringUtils.hasText(request.getRuleKey()) && !request.getRuleKey().equals(ruleKey)) {
            validateRuleKeyUniqueness(request.getRuleKey());
            rule.setRuleKey(request.getRuleKey());
            hasChanges = true;
        }

        if (request.getParameters() != null && !request.getParameters().isEmpty()) {
            // Merge and validate parameters
            Map<String, Object> existingParams = rule.getParameters();
            existingParams.putAll(request.getParameters());

            // Validate parameters based on rule type
            validateRuleParameters(rule.getRuleType(), existingParams);

            rule.setParameters(existingParams);

            // Update vehicle IDs from parameters
            Set<String> newVehicleIds = extractVehicleIdsFromParameters(existingParams);
            rule.setVehicleIds(newVehicleIds);

            hasChanges = true;
        }

        if (request.getPriority() > 0) {
            rule.setPriority(request.getPriority());
            hasChanges = true;
        }

        if (hasChanges) {
            rule.setUpdatedAt(clock.instant());
            AlertRule updated = ruleRepository.save(rule);

            // Update cache
            ruleCacheService.cacheRule(updated);

            // Invalidate vehicle caches if vehicle associations changed
            if (!oldVehicleIds.equals(rule.getVehicleIds())) {
                Set<String> allAffectedVehicles = new HashSet<>(oldVehicleIds);
                allAffectedVehicles.addAll(rule.getVehicleIds());
                allAffectedVehicles.forEach(vehicleRuleCacheService::invalidateVehicleRules);
            }

            if (!ruleKey.equals(updated.getRuleKey())) {
                ruleCacheService.evictRule(ruleKey);
            }

            // Invalidate pagination cache
            invalidatePaginationCache();

            logger.info("Alert rule updated successfully: {}", ruleKey);
            return toResponse(updated);
        }

        logger.debug("No changes detected for rule: {}", ruleKey);
        return toResponse(rule);
    }

    @Transactional
    @CacheEvict(value = {"alertRules", "alertRulesPaged", "vehicleRules"}, allEntries = true)
    public void enableRule(String ruleKey) {
        logger.info("Enabling alert rule: {}", ruleKey);

        AlertRule rule = ruleRepository.findByRuleKeyAndOrganizationId(ruleKey, TenantContext.getCurrentOrgId())
                .orElseThrow(() -> AlertException.ruleNotFound(ruleKey));

        if (!Boolean.TRUE.equals(rule.isEnabled())) {
            rule.setIsEnabled(true);
            rule.setUpdatedAt(clock.instant());
            ruleRepository.save(rule);

            // Update cache
            ruleCacheService.cacheRule(rule);

            // Invalidate vehicle caches
            rule.getVehicleIds().forEach(vehicleRuleCacheService::invalidateVehicleRules);

            // Invalidate pagination cache
            invalidatePaginationCache();

            logger.info("Alert rule enabled: {}", ruleKey);
        } else {
            logger.debug("Rule already enabled: {}", ruleKey);
        }
    }

    @Transactional
    @CacheEvict(value = {"alertRules", "alertRulesPaged", "vehicleRules"}, allEntries = true)
    public void disableRule(String ruleKey) {
        logger.info("Disabling alert rule: {}", ruleKey);

        AlertRule rule = ruleRepository.findByRuleKeyAndOrganizationId(ruleKey, TenantContext.getCurrentOrgId())
                .orElseThrow(() -> AlertException.ruleNotFound(ruleKey));

        if (Boolean.TRUE.equals(rule.isEnabled())) {
            rule.setIsEnabled(false);
            rule.setUpdatedAt(clock.instant());
            ruleRepository.save(rule);

            // Remove from cache
            ruleCacheService.evictRule(ruleKey);

            // Invalidate vehicle caches
            rule.getVehicleIds().forEach(vehicleRuleCacheService::invalidateVehicleRules);

            // Invalidate pagination cache
            invalidatePaginationCache();

            logger.info("Alert rule disabled: {}", ruleKey);
        } else {
            logger.debug("Rule already disabled: {}", ruleKey);
        }
    }

    @Transactional
    @CacheEvict(value = {"alertRules", "alertRulesPaged", "vehicleRules"}, allEntries = true)
    public void deleteRule(String ruleKey) {
        logger.info("Deleting alert rule: {}", ruleKey);

        AlertRule rule = ruleRepository.findByRuleKeyAndOrganizationId(ruleKey, TenantContext.getCurrentOrgId())
                .orElseThrow(() -> AlertException.ruleNotFound(ruleKey));

        Set<String> affectedVehicles = rule.getVehicleIds();

        ruleRepository.deleteByRuleKeyAndOrganizationId(ruleKey, TenantContext.getCurrentOrgId());

        // Remove from cache
        ruleCacheService.evictRule(ruleKey);

        // Invalidate vehicle caches
        affectedVehicles.forEach(vehicleRuleCacheService::invalidateVehicleRules);

        // Invalidate pagination cache
        invalidatePaginationCache();

        logger.info("Alert rule deleted successfully: {}", ruleKey);
    }

    // ========== HELPER METHODS ==========

    private Pageable createPageable(SearchRequest searchRequest) {
        Sort sort = Sort.by(searchRequest.getSortDirection(), searchRequest.getSortBy());
        return PageRequest.of(searchRequest.getPage(), searchRequest.getSize(), sort);
    }

    private void invalidatePaginationCache() {
        try {
            if (TransactionalCacheInvalidation.defer(redisTemplate, keyGenerator.getAllRuleKeysPattern())) return;
            List<String> keys = RedisKeyScanner.scanKeys(redisTemplate, keyGenerator.getPaginatedRulesPattern());
            if (!keys.isEmpty()) {
                redisTemplate.delete(keys);
                logger.debug("Invalidated pagination cache for rules");
            }
        } catch (Exception e) {
            logger.warn("Failed to invalidate pagination cache", e);
        }
    }

    private AlertRule saveAndCacheRule(AlertRule rule) {
        // Set timestamps
        Instant now = clock.instant();
        if (rule.getCreatedAt() == null) {
            rule.setCreatedAt(now);
        }
        rule.setUpdatedAt(now);

        // Save to database
        AlertRule saved = ruleRepository.save(rule);

        // Cache the rule
        ruleCacheService.cacheRule(saved);

        // Invalidate affected vehicle caches
        saved.getVehicleIds().forEach(vehicleRuleCacheService::invalidateVehicleRules);

        // Invalidate pagination cache
        invalidatePaginationCache();

        return saved;
    }

    private void validateRuleKeyUniqueness(String ruleKey) {
        if (ruleRepository.existsByRuleKeyAndOrganizationId(ruleKey, TenantContext.getCurrentOrgId())) {
            throw AlertException.ruleAlreadyExists(ruleKey);
        }
    }

    private void validateOverspeedRuleRequest(OverspeedRuleTemplateRequest request) {
        if (request.getSpeedLimit() <= 0) {
            throw new IllegalArgumentException("Speed limit must be positive");
        }
        if (request.getBuffer() < 0) {
            throw new IllegalArgumentException("Buffer must be non-negative");
        }
        if (CollectionUtils.isEmpty(request.getVehicleIds())) {
            throw new IllegalArgumentException("At least one vehicle ID is required");
        }
    }

    private void validateIdleTimeoutRuleRequest(IdleTimeoutRuleTemplateRequest request) {
        if (request.getMaxIdleMinutes() <= 0) {
            throw new IllegalArgumentException("Max idle minutes must be positive");
        }
        if (CollectionUtils.isEmpty(request.getVehicleIds())) {
            throw new IllegalArgumentException("At least one vehicle ID is required");
        }
    }

    private SpeedRuleParameters buildOverspeedParameters(OverspeedRuleTemplateRequest request) {
        return new SpeedRuleParameters(
                request.getSpeedLimit(),
                request.getBuffer(),
                "CRITICAL",
                new ArrayList<>(request.getVehicleIds()),
                "km/h",
                60
        );
    }

    private IdleRuleParameters buildIdleTimeoutParameters(IdleTimeoutRuleTemplateRequest request) {
        return new IdleRuleParameters(
                request.getMaxIdleMinutes(),
                "WARNING",
                new ArrayList<>(request.getVehicleIds()),
                false,
                request.getMaxIdleMinutes() / 2
        );
    }

    private GeofenceRuleParameters buildGeofenceParameters(GeofenceRuleTemplateRequest request,
                                                           Geofence geofence) {
        return new GeofenceRuleParameters(
                request.getGeofenceId(),
                geofence.getName(),
                request.getAction().name(),
                "WARNING",
                new ArrayList<>(request.getVehicleIds()),
                geofence.getShapeType() != null ? geofence.getShapeType().name() : null,
                geofence.getCenterLatitude(),
                geofence.getCenterLongitude(),
                geofence.getRadiusMeters(),
                0L
        );
    }

    private String buildOverspeedActions() {
        Map<String, Object> actions = new HashMap<>();
        actions.put("sendNotification", true);
        actions.put("executeCommand", false);
        actions.put("notificationChannels", Arrays.asList("SMS", "EMAIL", "DASHBOARD"));
        actions.put("escalationEnabled", true);
        actions.put("escalationAfterMinutes", 5);

        try {
            return objectMapper.writeValueAsString(actions);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize actions", e);
        }
    }

    private AlertRule createBaseRule(Object request, AlertRuleType ruleType,
                                     RuleParameters parameters) {
        AlertRule rule = new AlertRule();

        if (request instanceof OverspeedRuleTemplateRequest overspeedRequest) {
            rule.setRuleKey(overspeedRequest.getRuleKey());
            rule.setRuleName(overspeedRequest.getRuleName());
            rule.setPriority(overspeedRequest.getPriority());
            rule.setIsEnabled(overspeedRequest.isEnabled());
        } else if (request instanceof IdleTimeoutRuleTemplateRequest idleRequest) {
            rule.setRuleKey(idleRequest.getRuleKey());
            rule.setRuleName(idleRequest.getRuleName());
            rule.setPriority(idleRequest.getPriority());
            rule.setIsEnabled(idleRequest.isEnabled());
        } else if (request instanceof GeofenceRuleTemplateRequest geofenceRequest) {
            rule.setRuleKey(geofenceRequest.getRuleKey());
            rule.setRuleName(geofenceRequest.getRuleName());
            rule.setPriority(geofenceRequest.getPriority());
            rule.setIsEnabled(geofenceRequest.isEnabled());
        }

        rule.setRuleType(ruleType);
        rule.setParameters(ruleParametersMapper.toMap(parameters));
        rule.setCooldownMinutes(5);
        rule.setCreatedAt(clock.instant());
        rule.setUpdatedAt(clock.instant());
        rule.setOrganizationId(TenantContext.getCurrentOrgId());

        return rule;
    }

    private AlertRuleType parseRuleType(String ruleTypeStr) {
        try {
            return AlertRuleType.valueOf(ruleTypeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid rule type: " + ruleTypeStr +
                    ". Valid values: " + Arrays.toString(AlertRuleType.values()));
        }
    }

    private Map<String, Object> parseAndValidateParameters(String parametersJson, AlertRuleType ruleType) {
        Map<String, Object> parameters = parseParameters(parametersJson);
        validateRuleParameters(ruleType, parameters);
        return parameters;
    }

    private Map<String, Object> parseParameters(String parametersJson) {
        try {
            if (!StringUtils.hasText(parametersJson)) {
                return new HashMap<>();
            }
            return objectMapper.readValue(parametersJson, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid parameters JSON format: " + e.getMessage(), e);
        }
    }

    private Set<String> extractVehicleIdsFromParameters(Map<String, Object> parameters) {
        Set<String> vehicleIds = new HashSet<>();

        if (parameters != null && parameters.containsKey("vehicleIds")) {
            Object vehicleIdsObj = parameters.get("vehicleIds");
            if (vehicleIdsObj instanceof Collection) {
                ((Collection<?>) vehicleIdsObj).forEach(id -> {
                    if (id != null) {
                        vehicleIds.add(id.toString().trim());
                    }
                });
            } else if (vehicleIdsObj instanceof String) {
                // Handle comma-separated string
                String[] ids = ((String) vehicleIdsObj).split(",");
                for (String id : ids) {
                    if (StringUtils.hasText(id)) {
                        vehicleIds.add(id.trim());
                    }
                }
            }
        }

        return vehicleIds;
    }

    private void validateVehicleGeofenceAssociation(Geofence geofence, Set<String> vehicleIds) {
        for (String vehicleId : vehicleIds) {
            if (!geofence.hasVehicle(vehicleId)) {
                throw new IllegalArgumentException(
                        String.format("Vehicle %s is not associated with geofence %s",
                                vehicleId, geofence.getGeofenceId()));
            }
        }
    }

    private void validateRuleParameters(AlertRuleType ruleType, Map<String, Object> parameters) {
        if (parameters == null) {
            throw new IllegalArgumentException("Parameters cannot be null");
        }

        switch (ruleType) {
            case SPEED:
                validateSpeedRuleParameters(parameters);
                break;
            case TIME:
                validateTimeRuleParameters(parameters);
                break;
            case GEOFENCE:
                validateGeofenceRuleParameters(parameters);
                break;
            default:
                // Basic validation for custom rule types
                if (!parameters.containsKey("vehicleIds") ||
                        ((Collection<?>) parameters.get("vehicleIds")).isEmpty()) {
                    throw new IllegalArgumentException("Custom rules require at least one vehicle ID");
                }
                break;
        }
    }

    private void validateSpeedRuleParameters(Map<String, Object> parameters) {
        if (!parameters.containsKey("speedLimit")) {
            throw new IllegalArgumentException("Speed rule requires 'speedLimit' parameter");
        }
        Object speedLimit = parameters.get("speedLimit");
        if (!(speedLimit instanceof Number)) {
            throw new IllegalArgumentException("'speedLimit' must be a number");
        }
        if (((Number) speedLimit).floatValue() <= 0) {
            throw new IllegalArgumentException("'speedLimit' must be positive");
        }

        // Optional buffer validation
        if (parameters.containsKey("buffer")) {
            Object buffer = parameters.get("buffer");
            if (!(buffer instanceof Number)) {
                throw new IllegalArgumentException("'buffer' must be a number");
            }
            if (((Number) buffer).floatValue() < 0) {
                throw new IllegalArgumentException("'buffer' cannot be negative");
            }
        }
    }

    private void validateTimeRuleParameters(Map<String, Object> parameters) {
        if (!parameters.containsKey("maxIdleMinutes")) {
            throw new IllegalArgumentException("Time rule requires 'maxIdleMinutes' parameter");
        }
        Object idleMinutes = parameters.get("maxIdleMinutes");
        if (!(idleMinutes instanceof Number)) {
            throw new IllegalArgumentException("'maxIdleMinutes' must be a number");
        }
        if (((Number) idleMinutes).intValue() <= 0) {
            throw new IllegalArgumentException("'maxIdleMinutes' must be positive");
        }
    }

    private void validateGeofenceRuleParameters(Map<String, Object> parameters) {
        if (!parameters.containsKey("geofenceId")) {
            throw new IllegalArgumentException("Geofence rule requires 'geofenceId' parameter");
        }

        String geofenceId = parameters.get("geofenceId").toString();
        if (!StringUtils.hasText(geofenceId)) {
            throw new IllegalArgumentException("Geofence ID cannot be empty");
        }

        // Validate geofence exists and is active
        geofenceRuleValidator.getValidatedGeofence(geofenceId);
    }

    // ========== RESPONSE CONVERSION ==========

    private AlertRuleResponse toResponse(AlertRule rule) {
        AlertRuleResponse response = new AlertRuleResponse();
        response.setRuleKey(rule.getRuleKey());
        response.setRuleName(rule.getRuleName());
        response.setRuleType(rule.getRuleType());
        response.setParameters(rule.getParameters());
        response.setPriority(rule.getPriority() != null ? rule.getPriority() : 5);
        response.setEnabled(Boolean.TRUE.equals(rule.isEnabled()));
        response.setCreatedAt(rule.getCreatedAt());
        response.setUpdatedAt(rule.getUpdatedAt());
        return response;
    }

    // ========== BATCH OPERATIONS ==========

    @Transactional
    @CacheEvict(value = {"alertRules", "alertRulesPaged", "vehicleRules"}, allEntries = true)
    public List<AlertRuleResponse> batchCreateRules(List<CreateAlertRuleRequest> requests) {
        logger.info("Batch creating {} alert rules", requests.size());

        List<AlertRuleResponse> responses = new ArrayList<>();

        for (CreateAlertRuleRequest request : requests) {
            try {
                AlertRuleResponse response = createRule(request);
                responses.add(response);
            } catch (Exception e) {
                logger.error("Failed to create rule {} in batch: {}",
                        request.getRuleKey(), e.getMessage());
                // Continue with other rules
            }
        }

        logger.info("Batch creation completed: {} successful, {} total",
                responses.size(), requests.size());

        return responses;
    }

    @Transactional
    @CacheEvict(value = {"alertRules", "alertRulesPaged", "vehicleRules"}, allEntries = true)
    public void batchEnableRules(Set<String> ruleKeys) {
        logger.info("Batch enabling {} alert rules", ruleKeys.size());

        int enabledCount = 0;
        for (String ruleKey : ruleKeys) {
            try {
                enableRule(ruleKey);
                enabledCount++;
            } catch (Exception e) {
                logger.error("Failed to enable rule {} in batch: {}", ruleKey, e.getMessage());
            }
        }

        logger.info("Batch enabling completed: {} enabled, {} total",
                enabledCount, ruleKeys.size());
    }

    @Transactional(readOnly = true)
    @Cacheable("vehicleRules")
    public Map<String, List<AlertRuleResponse>> getRulesByVehicleIds(Set<String> vehicleIds) {
        Map<String, List<AlertRuleResponse>> result = new HashMap<>();

        for (String vehicleId : vehicleIds) {
            List<AlertRule> vehicleRules = ruleRepository.findActiveRulesForVehicleAndOrganizationId(vehicleId, TenantContext.getCurrentOrgId());
            result.put(vehicleId, vehicleRules.stream()
                    .map(this::toResponse)
                    .collect(Collectors.toList()));
        }

        return result;
    }
}
