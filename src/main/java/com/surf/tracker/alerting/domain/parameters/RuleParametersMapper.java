package com.surf.tracker.alerting.domain.parameters;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.surf.tracker.alerting.domain.enums.AlertRuleType;

import java.util.LinkedHashMap;
import java.util.Map;

public class RuleParametersMapper {

    private final ObjectMapper objectMapper;

    public RuleParametersMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public RuleParameters fromMap(AlertRuleType type, Map<String, Object> map) {
        Map<String, Object> source = map == null ? new LinkedHashMap<>() : map;
        return switch (type) {
            case SPEED -> objectMapper.convertValue(source, SpeedRuleParameters.class);
            case TIME -> objectMapper.convertValue(source, IdleRuleParameters.class);
            case GEOFENCE -> objectMapper.convertValue(source, GeofenceRuleParameters.class);
            default -> throw new IllegalArgumentException("No typed parameters for rule type: " + type);
        };
    }

    public Map<String, Object> toMap(RuleParameters parameters) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (parameters instanceof SpeedRuleParameters p) {
            map.put("speedLimit", p.speedLimit());
            map.put("buffer", p.buffer());
            map.put("severity", p.severity());
            map.put("vehicleIds", p.vehicleIds());
            map.put("unit", p.unit());
            map.put("evaluationInterval", p.evaluationInterval());
        } else if (parameters instanceof IdleRuleParameters p) {
            map.put("maxIdleMinutes", p.maxIdleMinutes());
            map.put("severity", p.severity());
            map.put("vehicleIds", p.vehicleIds());
            map.put("ignoreEngineOff", p.ignoreEngineOff());
            map.put("notificationThreshold", p.notificationThreshold());
        } else if (parameters instanceof GeofenceRuleParameters p) {
            map.put("geofenceId", p.geofenceId());
            map.put("geofenceName", p.geofenceName());
            map.put("action", p.action());
            map.put("severity", p.severity());
            map.put("vehicleIds", p.vehicleIds());
            map.put("shapeType", p.shapeType());
            map.put("centerLatitude", p.centerLatitude());
            map.put("centerLongitude", p.centerLongitude());
            map.put("radiusMeters", p.radiusMeters());
            map.put("maxDwellMinutes", p.maxDwellMinutes());
        } else {
            throw new IllegalArgumentException("Unsupported rule parameters type: " + parameters.getClass());
        }
        return map;
    }
}
