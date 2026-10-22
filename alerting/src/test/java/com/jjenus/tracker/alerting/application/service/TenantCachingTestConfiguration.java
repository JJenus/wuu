package com.jjenus.tracker.alerting.application.service;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
import com.jjenus.tracker.alerting.infrastructure.config.CacheConfig;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

@Configuration(proxyBeanMethods = false)
@EnableCaching
@ComponentScan(basePackages = "com.jjenus.tracker.alerting.infrastructure.cache",
    useDefaultFilters = false, includeFilters = @ComponentScan.Filter(type = FilterType.REGEX,
    pattern = ".*\\.(TenantAlert.*CacheResolver|Alert.*CacheKeyGenerator)"))
class TenantCachingTestConfiguration implements CachingConfigurer {
    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheConfig().errorHandler();
    }
}
