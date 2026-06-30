package com.venvify.venvifycore.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.Components;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cấu hình Swagger/OpenAPI. UI: {@code /api/v1/swagger-ui.html}, JSON: {@code /api/v1/v3/api-docs}.
 * Đã khai báo sẵn security scheme "bearerAuth" (JWT) để khi có auth thì dùng nút Authorize.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI venvifyOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Venvify Core API")
                        .description("Online Event Platform — REST API")
                        .version("v1")
                        .contact(new Contact().name("Venvify")))
                // Gắn yêu cầu bảo mật global → Swagger UI mới đính token vào MỌI request.
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Dán access token JWT vào đây")));
    }
}
