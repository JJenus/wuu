package com.surf.tracker.main.api;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
public class ApiVersionConfig implements WebMvcConfigurer {
    public static final String V1 = "/api/v1";

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix(V1, HandlerTypePredicate.forAnnotation(ApiV1.class)
            .and(HandlerTypePredicate.forBasePackage("com.surf.tracker")));
    }
}
