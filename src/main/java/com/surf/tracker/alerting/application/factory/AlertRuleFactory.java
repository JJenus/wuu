package com.surf.tracker.alerting.application.factory;

import com.surf.tracker.alerting.application.service.GeofenceService;
import com.surf.tracker.alerting.domain.*;
import com.surf.tracker.alerting.domain.entity.AlertRule;
import com.surf.tracker.alerting.domain.entity.Geofence;
import com.surf.tracker.alerting.domain.enums.AlertRuleType;
import com.surf.tracker.alerting.domain.parameters.GeofenceRuleParameters;
import com.surf.tracker.alerting.domain.parameters.IdleRuleParameters;
import com.surf.tracker.alerting.domain.parameters.RuleParameters;
import com.surf.tracker.alerting.domain.parameters.RuleParametersMapper;
import com.surf.tracker.alerting.domain.parameters.SpeedRuleParameters;
import com.surf.tracker.shared.domain.LocationPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class AlertRuleFactory {

    private static final Logger logger = LoggerFactory.getLogger(AlertRuleFactory.class);

    private final GeofenceService geofenceService;
    private final RuleStateStore stateStore;
    private final Clock clock;
    private final RuleParametersMapper ruleParametersMapper;

    public AlertRuleFactory(GeofenceService geofenceService, RuleStateStore stateStore, Clock clock,
                            RuleParametersMapper ruleParametersMapper) {
        this.geofenceService = geofenceService;
        this.stateStore = stateStore;
        this.clock = clock;
        this.ruleParametersMapper = ruleParametersMapper;
    }

    /**
     * Convert AlertRule entity to appropriate IAlertRule implementation
     */
    public IAlertRule createDomainRule(AlertRule entity, String vehicleId) {
        if (entity == null || !entity.isEnabled()) {
            return null;
        }

        // Check if rule applies to this specific vehicle
        if (!entity.appliesToVehicle(vehicleId)) {
            return null;
        }

        try {
            return switch (entity.getRuleType()) {
                case SPEED -> createMaxSpeedRule(entity,
                        (SpeedRuleParameters) ruleParametersMapper.fromMap(AlertRuleType.SPEED, entity.getParameters()));
                case TIME -> createIdleTimeRule(entity,
                        (IdleRuleParameters) ruleParametersMapper.fromMap(AlertRuleType.TIME, entity.getParameters()));
                case GEOFENCE -> createGeofenceRule(entity,
                        (GeofenceRuleParameters) ruleParametersMapper.fromMap(AlertRuleType.GEOFENCE, entity.getParameters()),
                        vehicleId);
                default -> createGenericRule(entity, entity.getParameters());
            };
        } catch (Exception e) {
            logger.error("Failed to create domain rule for {}: {}",
                    entity.getRuleKey(), e.getMessage());
            return null;
        }
    }

    private MaxSpeedRule createMaxSpeedRule(AlertRule entity, SpeedRuleParameters params) {
        float speedLimit = params.speedLimit();
        return new MaxSpeedRule(entity.getRuleKey(), entity.getRuleName(), speedLimit);
    }

    private IdleTimeRule createIdleTimeRule(AlertRule entity, IdleRuleParameters params) {
        int maxIdleMinutes = params.maxIdleMinutes();
        Duration maxIdleTime = Duration.ofMinutes(maxIdleMinutes);
        java.util.Map<String, Instant> persistedTimes = stateStore.getAllLastMovementTimes(entity.getRuleKey());
        return new IdleTimeRule(entity.getRuleKey(), entity.getRuleName(), maxIdleTime, persistedTimes,
                                (vehicleId, time) -> stateStore.setLastMovementTime(entity.getRuleKey(), vehicleId, time));
    }

    private GeofenceRule createGeofenceRule(AlertRule entity, GeofenceRuleParameters params, String vehicleId) {
        String geofenceId = params.geofenceId();
        String actionStr = params.action();

        Geofence geofence = getGeofenceById(geofenceId);
        if (geofence == null) {
            logger.warn("Geofence not found: {} for rule {}", geofenceId, entity.getRuleKey());
            return null;
        }

        if (!geofence.hasVehicle(vehicleId)) {
            logger.debug("Geofence {} doesn't apply to vehicle {}", geofenceId, vehicleId);
            return null;
        }

        List<LocationPoint> boundaryPoints = extractBoundaryPoints(geofence);

        GeofenceRule.Action action;
        try {
            action = GeofenceRule.Action.valueOf(actionStr);
        } catch (IllegalArgumentException e) {
            action = GeofenceRule.Action.BOTH;
        }

        Optional<Boolean> previous = stateStore.getGeofenceWasInside(entity.getRuleKey(), vehicleId);
        long maxDwellMinutes = params.maxDwellMinutes();
        GeofenceRule rule = new GeofenceRule(
                entity.getRuleKey(),
                entity.getRuleName(),
                geofenceId,
                boundaryPoints,
                action,
                entity.getPriority(),
                entity.isEnabled(),
                maxDwellMinutes,
                v -> stateStore.getGeofenceEntryTime(entity.getRuleKey(), v),
                (v, t) -> stateStore.setGeofenceEntryTime(entity.getRuleKey(), v, t),
                () -> stateStore.clearGeofenceEntryTime(entity.getRuleKey(), vehicleId)
        );
        previous.ifPresent(rule::setWasInside);
        return rule;
    }

    private Geofence getGeofenceById(String geofenceId) {
        try {
            Long id = Long.parseLong(geofenceId);
            return geofenceService.getGeofenceById(id);
        } catch (NumberFormatException e) {
            logger.error("Invalid geofence ID format: {}", geofenceId);
            return null;
        }
    }

    private List<LocationPoint> extractBoundaryPoints(Geofence geofence) {
        if (geofence.getShapeType() == null) {
            return List.of();
        }

        // For circle geofences, create a polygon approximation
        if (geofence.getShapeType().name().equals("CIRCLE") &&
                geofence.getCenterLatitude() != null &&
                geofence.getCenterLongitude() != null &&
                geofence.getRadiusMeters() != null) {

            return createCircleBoundaryPoints(
                    geofence.getCenterLatitude(),
                    geofence.getCenterLongitude(),
                    geofence.getRadiusMeters()
            );
        }

        // For polygon geofences, use the stored points
        return geofence.getPoints().stream()
                .map(point -> new LocationPoint(
                        point.getLatitude(),
                        point.getLongitude(),
                        0.0f, // Speed not relevant for geofence points
                        clock.instant()
                ))
                .toList();
    }

    private List<LocationPoint> createCircleBoundaryPoints(Double lat, Double lon, Integer radius) {
        // Create 12-point polygon approximation of circle
        List<LocationPoint> points = new java.util.ArrayList<>();
        int pointsCount = 12;

        for (int i = 0; i < pointsCount; i++) {
            double angle = 2 * Math.PI * i / pointsCount;
            double latOffset = (radius / 111000.0) * Math.sin(angle);
            double lonOffset = (radius / (111000.0 * Math.cos(Math.toRadians(lat)))) * Math.cos(angle);

            points.add(new LocationPoint(
                    lat + latOffset,
                    lon + lonOffset,
                    0.0f,
                    clock.instant()
            ));
        }

        return points;
    }

    private IAlertRule createGenericRule(AlertRule entity, Map<String, Object> params) {
        return new GenericAlertRule(entity, params);
    }
}