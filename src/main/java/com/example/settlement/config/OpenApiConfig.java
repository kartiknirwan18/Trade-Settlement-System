package com.example.settlement.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/** Swagger / OpenAPI documentation configuration. */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI settlementOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Trade Settlement & Invoice Reconciliation System API")
                        .version("1.0.0")
                        .description("""
                                REST API for commodity trade settlement, invoice generation, payment tracking,
                                reconciliation and audit history.

                                **Authentication**: obtain a JWT from `POST /api/v1/auth/login` and send it as
                                `Authorization: Bearer <token>`. Roles: ADMIN, FINANCE, TRADER, AUDITOR.

                                **Money**: every monetary value is a decimal number with 2 decimal places.
                                **Dates**: business dates use `yyyy-MM-dd`; timestamps use ISO-8601 UTC.
                                """)
                        .contact(new Contact().name("Settlement Platform Team").email("platform@example.com"))
                        .license(new License().name("MIT")))
                .servers(List.of(new Server().url("/").description("Current host")))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme()
                                .name(BEARER_SCHEME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT issued by /api/v1/auth/login")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
