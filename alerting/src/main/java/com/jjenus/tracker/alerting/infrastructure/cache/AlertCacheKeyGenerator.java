package com.jjenus.tracker.alerting.infrastructure.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjenus.tracker.alerting.api.dto.AlertSearchRequest;
import com.jjenus.tracker.alerting.api.dto.SearchRequest;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Immutable, collision-free snapshots of query arguments, separated by operation. */
@Component("alertCacheKeyGenerator")
public class AlertCacheKeyGenerator implements KeyGenerator {
    private final ObjectMapper objectMapper;

    public AlertCacheKeyGenerator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Object generate(Object target, Method method, Object... params) {
        List<Object> key = new ArrayList<>();
        key.add(target.getClass().getSimpleName());
        key.add(method.getName());
        for (Object param : params) {
            if (param instanceof AlertSearchRequest request) {
                key.add(Arrays.asList(request.getPage(), request.getSize(), request.getSortBy(),
                    request.getSortDirection(), request.getSearch(), request.getVehicleId(), request.getTrackerId(),
                    request.getAlertType(), request.getSeverity(), request.getAcknowledged(), request.getResolved(),
                    request.getStartDate(), request.getEndDate()));
            } else if (param instanceof SearchRequest request) {
                key.add(Arrays.asList(request.getPage(), request.getSize(), request.getSortBy(),
                    request.getSortDirection(), request.getSearch(), request.getVehicleId(), request.getActive()));
            } else {
                key.add(param);
            }
        }
        try {
            return objectMapper.writeValueAsString(key);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Cannot encode alert cache key", e);
        }
    }
}
