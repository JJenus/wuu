package com.surf.tracker.main;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = TrackingEngineApplication.class, properties = {
    "userauth.jwt.secret=test-only-secret-with-at-least-thirty-two-bytes",
    "spring.datasource.url=jdbc:h2:mem:api-contract;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
    "spring.jms.listener.auto-startup=false"})
@AutoConfigureMockMvc
class ApiContractIntegrationTest {
    @Autowired @org.springframework.beans.factory.annotation.Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping mappings;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @Test void mappings_applicationControllers_haveExactlyOneVersionPrefix() {
        var applicationMappings = mappings.getHandlerMethods().entrySet().stream()
            .filter(entry -> entry.getValue().getBeanType().getPackageName().startsWith("com.surf.tracker"))
            .filter(entry -> entry.getValue().getBeanType().isAnnotationPresent(RestController.class))
            .toList();

        assertThat(applicationMappings).isNotEmpty();
        var operationIds = new java.util.HashSet<String>();
        for (var entry : applicationMappings) {
            assertThat(entry.getValue().getBeanType().getAnnotations())
                .anyMatch(annotation -> annotation.annotationType().getSimpleName().equals("ApiV1"));
            assertThat(entry.getKey().getPatternValues()).allSatisfy(path -> {
                assertThat(path).startsWith("/api/v1/");
                assertThat(path.substring("/api/v1".length())).doesNotContain("/api/");
            });
            var operation = entry.getValue().getMethodAnnotation(io.swagger.v3.oas.annotations.Operation.class);
            assertThat(operation).as(entry.getValue().toString()).isNotNull();
            assertThat(operation.operationId()).isNotBlank();
            assertThat(operation.summary()).isNotBlank();
            assertThat(operation.description()).isNotBlank();
            assertThat(operationIds.add(operation.operationId())).as("unique operation ID").isTrue();
        }
    }

    @Test @WithMockUser(authorities = "system.admin")
    void openApi_v1Group_containsCanonicalPathsAndBearerScheme() throws Exception {
        var response = mvc.perform(get("/v3/api-docs/api-v1"))
            .andExpect(status().isOk()).andReturn().getResponse();
        // OpenAPI is a genuinely dynamic schema document, inspected as a JSON tree.
        var document = mapper.readTree(response.getContentAsString());
        assertThat(document.path("paths").has("/api/v1/alerts")).isTrue();
        assertThat(document.path("paths").has("/api/v1/vehicles")).isTrue();
        assertThat(document.path("components").path("securitySchemes").path("bearerAuth").path("scheme").asText())
            .isEqualTo("bearer");
        document.path("paths").fieldNames().forEachRemaining(path -> assertThat(path).startsWith("/api/v1/"));
        assertThat(document.path("paths").path("/api/v1/auth/login").path("post").path("security").isEmpty()).isTrue();
        var disabled = document.path("paths").path("/api/v1/notifications/webhooks/push/{provider}").path("post");
        assertThat(disabled.path("responses").has("403")).isTrue();
        assertThat(disabled.path("responses").has("200")).isFalse();
    }

    @Test @WithMockUser(authorities = {"alerts.read", "alerts.write", "system.admin"})
    void routing_legacyAndUnknownVersionPaths_remainRejected() throws Exception {
        for (String path : java.util.List.of("/alerts", "/api/notifications", "/api/v2/alerts")) {
            mvc.perform(get(path)).andExpect(status().isForbidden());
        }
    }
}
