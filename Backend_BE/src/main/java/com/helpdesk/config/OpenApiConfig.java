package com.helpdesk.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearerAuth";

    @Bean
    public OpenAPI helpdeskOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Multi-Tenant Helpdesk API")
                        .version("1.0.0")
                        .description("""
                                Ticketing API where every organization only sees its own users and tickets.

                                1. Call `POST /api/auth/login` (demo users are listed in the README).
                                2. Click **Authorize** and paste the `accessToken`.
                                """))
                .addSecurityItem(new SecurityRequirement().addList(BEARER))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")));
    }
}
