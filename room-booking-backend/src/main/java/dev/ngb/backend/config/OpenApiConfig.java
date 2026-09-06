package dev.ngb.backend.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Defines shared metadata and authentication semantics for the generated OpenAPI contract.
 *
 * <p>{@code @Configuration} makes the {@link OpenAPI} bean available to Springdoc. The bearer
 * scheme documents the access JWT accepted by protected API operations without exposing any
 * signing material or token values.</p>
 */
@Configuration
public class OpenApiConfig {

    /**
     * Creates the top-level OpenAPI description used by Swagger UI and the JSON/YAML contracts.
     *
     * @return OpenAPI metadata with the reusable HTTP bearer authentication scheme
     */
    @Bean
    OpenAPI roomBookingOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Room Booking API")
                        .version("v1"))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT"))
                        .addResponses("ValidationError", errorResponse("Request validation failure (VALIDATION_ERROR)"))
                        .addResponses("Unauthorized", errorResponse("Missing, expired, or invalid access token"))
                        .addResponses("AccountDisabled", errorResponse("Account is not active (USER_ACCOUNT_DISABLED)"))
                        .addResponses("UserNotFound", errorResponse("User no longer exists (USER_NOT_FOUND)"))
                        .addResponses("InternalServerError", errorResponse("Unexpected server failure")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }

    private ApiResponse errorResponse(String description) {
        return new ApiResponse()
                .description(description)
                .content(new Content().addMediaType("application/json", new MediaType()
                        .schema(new Schema<>().$ref("#/components/schemas/ApiErrorResponse"))));
    }
}
