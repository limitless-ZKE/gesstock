package com.gesstock.gesstock.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenAPIConfig {

    private static final String SESSION_SCHEME = "sessionAuth";

    @Bean
    public OpenAPI gesStockOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("GesStock")
                .version("3.0")
                .description("API GesStock 3.0 pour la gestion des stocks, des ventes, des approvisionnements, des caisses et des rapports financiers."))
            .components(new Components()
                .addSecuritySchemes(SESSION_SCHEME, new SecurityScheme()
                    .type(SecurityScheme.Type.APIKEY)
                    .in(SecurityScheme.In.COOKIE)
                    .name("JSESSIONID")
                    .description("Session obtenue via POST /login ; les requêtes d'écriture exigent aussi l'en-tête X-XSRF-TOKEN.")))
            .addSecurityItem(new SecurityRequirement().addList(SESSION_SCHEME));
    }
}
