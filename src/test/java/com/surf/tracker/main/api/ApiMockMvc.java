package com.surf.tracker.main.api;

import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/** Framework adapter: controllers are heterogeneous handler objects, not domain DTOs. */
public final class ApiMockMvc {
    private ApiMockMvc() {}

    public static StandaloneMockMvcBuilder standaloneSetup(Object... controllers) {
        return MockMvcBuilders.standaloneSetup(controllers).setCustomHandlerMapping(() -> {
            var mapping = new RequestMappingHandlerMapping();
            // Spring's mapping API requires a prefix-to-type-predicate map.
            mapping.setPathPrefixes(java.util.Collections.singletonMap(ApiVersionConfig.V1,
                HandlerTypePredicate.forAnnotation(ApiV1.class)
                    .and(HandlerTypePredicate.forBasePackage("com.surf.tracker"))));
            return mapping;
        });
    }
}
