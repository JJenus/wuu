package com.surf.tracker.main.api;

import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {
    @Bean
    public io.swagger.v3.core.jackson.ModelResolver apiModelResolver(com.fasterxml.jackson.databind.ObjectMapper mapper) {
        // Match the application's actual JSON naming/modules, not Swagger's default camelCase mapper.
        return new io.swagger.v3.core.jackson.ModelResolver(mapper);
    }

    private static final java.util.Set<String> DISABLED_HTTP_OPERATIONS = java.util.Set.of(
        "commandsMarkSent", "commandsMarkDelivered", "commandsMarkFailed", "alertsCleanup",
        "alertsAutomated", "webhooksPush", "webhooksEmail");
    @Bean
    public OpenAPI trackerOpenApi() {
        return new OpenAPI().info(new Info().title("Tracker REST API").version("1")
            .description("Canonical v1 contract. All application REST paths start with /api/v1. "
                + "Bearer tokens require an active server-side session; current database permissions and tenant membership are authoritative. "
                + "Documentation access requires system.admin. No legacy URL aliases or version fallback are provided. "
                + "Mapped internal operations explicitly denied by HTTP security are identified in their descriptions."))
            .components(new Components().addSecuritySchemes("bearerAuth", new SecurityScheme()
                .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")));
        // Do not set servers to /api/v1: generated operation paths already include it.
    }

    @Bean
    public GroupedOpenApi apiV1() {
        return GroupedOpenApi.builder().group("api-v1").pathsToMatch("/api/v1/**")
            .addOperationCustomizer(securityDocumentation()).build();
    }

    @Bean
    public OperationCustomizer securityDocumentation() {
        return (operation, handler) -> {
            if (!handler.getBeanType().isAnnotationPresent(ApiV1.class)) return operation;
            boolean publicOperation = handler.hasMethodAnnotation(SecurityRequirements.class);
            operation.setSecurity(publicOperation ? List.of()
                : List.of(new SecurityRequirement().addList("bearerAuth")));
            // HTTP security has its own minimal {code} body, distinct from MVC ErrorResponse.
            // This inline OpenAPI schema describes that external wire shape, not a domain model.
            operation.getResponses().addApiResponse("401", new ApiResponse()
                .description("Missing/invalid credentials, inactive session, or disabled public registration. Auth-service errors may also include message.")
                .content(securityErrorContent("UNAUTHENTICATED")));
            if (!publicOperation) {
                operation.getResponses().addApiResponse("403", new ApiResponse()
                    .description("Authenticated caller lacks the required permission or this HTTP operation is explicitly disabled.")
                    .content(securityErrorContent("FORBIDDEN")));
            }
            operation.getResponses().forEach((code, response) -> {
                if (java.util.Set.of("400", "404", "409").contains(code)) {
                    // These documented MVC failures are rendered by GlobalExceptionHandler,
                    // never by serializing the operation's successful response DTO.
                    response.setContent(mvcErrorContent());
                }
            });
            // A mapped Java handler is not a usable HTTP operation when SecurityConfig denies it.
            if (DISABLED_HTTP_OPERATIONS.contains(operation.getOperationId())) {
                operation.getResponses().keySet().removeIf(code -> code.startsWith("2"));
            }
            return operation;
        };
    }

    private Content securityErrorContent(String code) {
        var schema = new ObjectSchema();
        schema.addProperty("code", new StringSchema().example(code));
        schema.addProperty("message", new StringSchema().description("Optional explanation on auth-service errors."));
        schema.setRequired(List.of("code"));
        return new Content().addMediaType("application/json", new MediaType().schema(schema));
    }

    private Content mvcErrorContent() {
        var schema = new ObjectSchema();
        schema.addProperty("code", new StringSchema().description("Machine-readable error code."));
        schema.addProperty("message", new StringSchema().description("Error explanation."));
        schema.addProperty("timestamp", new StringSchema().format("date-time")
            .description("Currently defaults to the epoch; not a reliable occurrence time."));
        // Validation field names are genuinely dynamic keys from BindingResult.
        var fieldErrors = new ObjectSchema();
        fieldErrors.setAdditionalProperties(new StringSchema());
        schema.addProperty("field_errors", fieldErrors);
        return new Content().addMediaType("application/json", new MediaType().schema(schema));
    }
}
