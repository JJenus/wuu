package com.surf.tracker.alerting.infrastructure.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.surf.tracker.alerting.api.dto.SearchRequest;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/** Snapshot mutable request fields; do not key queries by object identity or hashCode. */
@Component("alertRuleCacheKeyGenerator")
public class AlertRuleCacheKeyGenerator implements KeyGenerator {
    private final ObjectMapper objectMapper;

    public AlertRuleCacheKeyGenerator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Object generate(Object target, Method method, Object... params) {
        List<Object> key = new ArrayList<>();
        key.add(method.getName());
        for (Object param : params) {
            if (param instanceof SearchRequest request) {
                key.add(Arrays.asList(request.getPage(), request.getSize(), request.getSortBy(),
                    request.getSortDirection(), request.getSearch(), request.getRuleType(),
                    method.getName().equals("getEnabledRulesPaged") ? Boolean.TRUE : request.getEnabled()));
            } else if (param instanceof Set<?> ids) {
                key.add(ids.stream().map(id -> (String) id).sorted().toList());
            } else {
                key.add(param);
            }
        }
        try {
            return objectMapper.writeValueAsString(key);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Cannot build alert-rule cache key", e);
        }
    }
}
