package com.cimportal.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    OpenAPI cimPortalOpenApi() {
        final String scheme = "bearer-jwt";
        return new OpenAPI()
            .info(new Info().title("CIMS 门户 API").version("v1")
                .description("CIMS 门户后端 REST API。契约见 docs/api/api-reference.md。"))
            .components(new Components().addSecuritySchemes(scheme,
                new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
            .addSecurityItem(new SecurityRequirement().addList(scheme));
    }
}
