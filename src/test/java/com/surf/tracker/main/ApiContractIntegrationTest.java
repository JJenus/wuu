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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
        assertThat(applicationMappings).hasSize(107);
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
        java.nio.file.Files.writeString(java.nio.file.Path.of("target/api-v1.json"), document.toPrettyString());
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

    @Test void documentation_anonymousCaller_isDenied() throws Exception {
        mvc.perform(get("/v3/api-docs/api-v1")).andExpect(status().isUnauthorized());
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isUnauthorized());
    }

    @Test @WithMockUser(authorities = "system.admin")
    void openApi_schemas_matchJacksonWireNamesAndResponseContracts() throws Exception {
        var response = mvc.perform(get("/v3/api-docs/api-v1")).andExpect(status().isOk()).andReturn().getResponse();
        var document = mapper.readTree(response.getContentAsString());
        var schemas = document.path("components").path("schemas");
        var vehicle = schemas.path("CreateVehicleRequest");
        assertThat(vehicle.path("properties").has("vehicle_id")).isTrue();
        assertThat(vehicle.path("properties").has("vehicleId")).isFalse();
        assertThat(vehicle.path("required").toString()).contains("vehicle_id").doesNotContain("vehicleId");
        for (Class<?> type : java.util.List.of(com.surf.tracker.core.api.dto.VehicleResponse.class,
                com.surf.tracker.core.api.dto.TripHistoryResponse.class,
                com.surf.tracker.subscription.api.dto.EnrollmentResponse.class,
                com.surf.tracker.userauth.application.dto.ErrorResponse.class)) {
            var properties = schemas.path(type.getSimpleName()).path("properties");
            mapper.getSerializationConfig().introspect(mapper.constructType(type)).findProperties().forEach(property ->
                assertThat(properties.has(property.getName())).as(type.getSimpleName() + "." + property.getName()).isTrue());
        }
        var paths = document.path("paths");
        var csv = paths.path("/api/v1/trips/export").path("get").path("responses").path("200");
        assertThat(csv.path("content").has("text/csv")).isTrue();
        assertThat(csv.path("headers").has("Content-Disposition")).isTrue();
        var enroll = paths.path("/api/v1/subscriptions").path("post").path("responses");
        assertThat(enroll.has("200") && enroll.has("201") && enroll.has("409")).isTrue();
        assertThat(paths.path("/api/v1/auth/logout").path("post").path("responses").path("204").has("content")).isFalse();
        assertThat(schemas.path("ChangePasswordRequest").path("properties").path("new_password").path("minLength").asInt()).isEqualTo(8);
        schemas.path("NotificationResponse").path("properties").forEach(property ->
            assertThat(property.path("description").asText()).isNotBlank());
        var notificationParameters = paths.path("/api/v1/notifications/user/{userId}").path("get").path("parameters");
        assertThat(notificationParameters.findValuesAsText("description").toString()).contains("OPENED event");
    }

    @Test @WithMockUser(authorities = "trips.read")
    void documentation_nonAdministrator_isDenied() throws Exception {
        mvc.perform(get("/v3/api-docs/api-v1")).andExpect(status().isForbidden());
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isForbidden());
    }

    @Test @WithMockUser(authorities = "system.admin")
    void routing_disabledHandlers_cannotBeInvokedEvenByAdministrator() throws Exception {
        for (String path : java.util.List.of("/api/v1/commands/1/send", "/api/v1/commands/1/deliver",
                "/api/v1/commands/1/fail", "/api/v1/alerts/cleanup", "/api/v1/alerts/automated",
                "/api/v1/notifications/webhooks/push/test", "/api/v1/notifications/webhooks/email/test")) {
            mvc.perform(post(path).contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        }
    }

    @Test @WithMockUser(authorities = "system.admin")
    void openApi_everyMappedOperation_hasUniqueMetadataSecurityAndResolvableSchemas() throws Exception {
        var response = mvc.perform(get("/v3/api-docs/api-v1")).andExpect(status().isOk()).andReturn().getResponse();
        var document = mapper.readTree(response.getContentAsString());
        var expectedIds = mappings.getHandlerMethods().values().stream()
            .filter(handler -> handler.getBeanType().isAnnotationPresent(com.surf.tracker.main.api.ApiV1.class))
            .map(handler -> handler.getMethodAnnotation(io.swagger.v3.oas.annotations.Operation.class).operationId())
            .collect(java.util.stream.Collectors.toSet());
        var actualIds = new java.util.HashSet<String>();
        document.path("paths").forEach(path -> path.forEach(operation -> {
            String id = operation.path("operationId").asText();
            assertThat(actualIds.add(id)).as("unique generated operation ID: " + id).isTrue();
            assertThat(operation.path("description").asText()).isNotBlank();
            assertThat(operation.path("summary").asText()).isNotBlank();
            boolean publicAuth = java.util.Set.of("authLogin", "authRegister", "authRefresh").contains(id);
            assertThat(operation.path("security").isEmpty()).as(id).isEqualTo(publicAuth);
            if (!publicAuth) assertThat(operation.path("security").get(0).has("bearerAuth")).as(id).isTrue();
            assertThat(operation.path("responses").has("401")).as(id).isTrue();
        }));
        assertThat(actualIds).containsExactlyInAnyOrderElementsOf(expectedIds);
        for (var ref : document.findValues("$ref")) {
            assertThat(ref.asText()).startsWith("#/");
            assertThat(document.at(ref.asText().substring(1)).isMissingNode()).as(ref.asText()).isFalse();
        }
    }

    @Test void routing_everyProtectedV1Operation_rejectsAnonymousAccessBeforeBinding() throws Exception {
        for (var entry : mappings.getHandlerMethods().entrySet()) {
            var handler = entry.getValue();
            if (!handler.getBeanType().isAnnotationPresent(com.surf.tracker.main.api.ApiV1.class)
                    || handler.hasMethodAnnotation(io.swagger.v3.oas.annotations.security.SecurityRequirements.class)) continue;
            for (var path : entry.getKey().getPatternValues())
                for (var method : entry.getKey().getMethodsCondition().getMethods())
                    mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request(
                        org.springframework.http.HttpMethod.valueOf(method.name()), path.replaceAll("\\{[^}]+}", "1")))
                        .andExpect(status().isUnauthorized());
        }
    }

    @Test @WithMockUser(authorities = {"alerts.read", "alerts.write", "system.admin"})
    void routing_legacyAndUnknownVersionPaths_remainRejected() throws Exception {
        for (String path : java.util.List.of("/alerts", "/api/notifications", "/api/v2/alerts")) {
            mvc.perform(get(path)).andExpect(status().isForbidden());
        }
    }
}
