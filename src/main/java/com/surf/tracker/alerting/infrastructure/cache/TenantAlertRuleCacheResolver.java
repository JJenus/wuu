package com.surf.tracker.alerting.infrastructure.cache;

import com.surf.tracker.shared.security.TenantContext;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.interceptor.CacheOperationInvocationContext;
import org.springframework.cache.interceptor.CacheResolver;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Objects;

/** Separate cache regions make even allEntries eviction tenant-local. */
@Component("tenantAlertRuleCacheResolver")
public class TenantAlertRuleCacheResolver implements CacheResolver {
    private final CacheManager cacheManager;

    public TenantAlertRuleCacheResolver(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @Override
    public Collection<? extends Cache> resolveCaches(CacheOperationInvocationContext<?> context) {
        Long organizationId = TenantContext.getCurrentOrgId();
        if (organizationId == null || organizationId <= 0) {
            throw new IllegalStateException("Organization context is required for alerting caching");
        }
        // The versioned namespace never reads legacy, globally shared entries.
        return context.getOperation().getCacheNames().stream()
            .map(name -> Objects.requireNonNull(
                cacheManager.getCache(name + ":v2:org:" + organizationId), "Missing tenant cache"))
            .toList();
    }
}
