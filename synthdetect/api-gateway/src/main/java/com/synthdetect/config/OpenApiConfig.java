package com.synthdetect.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("SynthDetect API")
                        .version("1.0.0")
                        .description("AI Synthetic Content Detection Platform — Detect AI-generated images and text, " +
                                "generate compliance labels per India's IT Rules 2026.")
                        .contact(new Contact()
                                .name("SynthDetect")
                                .email("support@synthdetect.com")))
                .addSecurityItem(new SecurityRequirement().addList("bearer-key"))
                .components(new Components()
                        .addSecuritySchemes("bearer-key",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("API Key or JWT")
                                        .description("Use API key (sd_live_xxx) or JWT token")));
    }
}
