package com.lezifx.trading.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI leziFxOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("LeziFx Trading Platform API")
                .description("""
                    Multi-tenant prediction-trading platform API.

                    ## Authentication
                    1. Call `GET /public/config` with `X-Domain: your-app-domain.com` — returns your `apiKey`
                    2. Include `X-API-Key: {apiKey}` on all subsequent requests
                    3. Call `POST /auth/login` — returns `accessToken` and `refreshToken`
                    4. Include `Authorization: Bearer {accessToken}` on all authenticated endpoints
                    """)
                .version("1.0")
                .contact(new Contact()
                    .name("LeziFx Developer Support")
                    .email("mdaucodes@gmail.com")
                    .url("https://lezifx.com/docs")))
            .servers(List.of(
                new Server()
                    .url("https://lezifx-backend-java-spring-boot-production.up.railway.app")
                    .description("Production"),
                new Server()
                    .url("http://localhost:8080")
                    .description("Local development")))
            .addSecurityItem(new SecurityRequirement()
                .addList("bearerAuth")
                .addList("apiKey"))
            .components(new Components()
                .addSecuritySchemes("bearerAuth",
                    new SecurityScheme()
                        .name("bearerAuth")
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Access token from POST /auth/login. Expires in 15 minutes."))
                .addSecuritySchemes("apiKey",
                    new SecurityScheme()
                        .name("X-API-Key")
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .description("Tenant API key from GET /public/config")));
    }
}
