package com.gesstock.gesstock.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.Customizer;
import org.springframework.http.HttpMethod;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final List<String> allowedOrigins;

    public SecurityConfig(
        @Value("${app.cors.allowed-origins:http://localhost:5173}") List<String> allowedOrigins
    ) {
        this.allowedOrigins = allowedOrigins;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // Authentification par cookie de session : la protection CSRF reste active.
            // Le jeton est exposé dans le cookie XSRF-TOKEN, relu par le frontend (axios)
            // puis renvoyé dans l'en-tête X-XSRF-TOKEN. Les formulaires Thymeleaf
            // (th:action) reçoivent le jeton automatiquement.
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
            .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
            .cors(Customizer.withDefaults())
            .exceptionHandling(exceptions -> exceptions
                .defaultAuthenticationEntryPointFor(
                    new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                    request -> request.getServletPath().startsWith("/api/")
                ))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/css/**", "/js/**", "/images/**", "/login",
                    "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**"
                ).permitAll()
                .requestMatchers("/api/referentiels/utilisateurs/**", "/api/referentiels/roles/**").hasRole("ADMIN")
                .requestMatchers("/api/referentiels/unites-mesure/**")
                    .hasAnyRole("ADMIN", "MANAGER", "GESTIONNAIRE")
                .requestMatchers("/api/referentiels/sessions-caisse/**")
                    .hasAnyRole("ADMIN", "MANAGER", "GESTIONNAIRE", "CAISSIER", "VENDEUR")
                .requestMatchers("/api/caisse/**")
                    .hasAnyRole("ADMIN", "MANAGER", "GESTIONNAIRE", "CAISSIER", "VENDEUR")
                .requestMatchers("/api/articles/**").hasAnyRole("ADMIN", "MANAGER", "GESTIONNAIRE", "VENDEUR")
                .requestMatchers("/api/fournisseurs/**", "/api/approvisionnements/**")
                    .hasAnyRole("ADMIN", "MANAGER", "GESTIONNAIRE")
                .requestMatchers(HttpMethod.POST, "/api/ventes/*/annulation")
                    .hasAnyRole("ADMIN", "MANAGER", "GESTIONNAIRE")
                .requestMatchers("/api/proformas/**", "/api/ventes/**")
                    .hasAnyRole("ADMIN", "MANAGER", "GESTIONNAIRE", "CAISSIER", "VENDEUR", "USER")
                .requestMatchers("/api/clients/**", "/api/dettes-clients/**")
                    .hasAnyRole("ADMIN", "MANAGER", "GESTIONNAIRE", "CAISSIER", "VENDEUR", "USER")
                .requestMatchers(HttpMethod.GET, "/api/promotions/**")
                    .hasAnyRole("ADMIN", "MANAGER", "GESTIONNAIRE", "CAISSIER", "VENDEUR")
                .requestMatchers("/api/promotions/**", "/api/statistiques/**", "/api/journal-activites/**")
                    .hasAnyRole("ADMIN", "MANAGER", "GESTIONNAIRE")
                .requestMatchers("/articles/**").hasAnyRole("ADMIN", "GESTIONNAIRE", "VENDEUR")
                .requestMatchers("/ventes/**").hasAnyRole("ADMIN", "VENDEUR", "USER")
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .defaultSuccessUrl("/", true)
                .permitAll()
            )
            .logout(logout -> logout
                .logoutSuccessUrl("/login?logout")
                .permitAll()
            );

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Content-Type", "Accept", "X-XSRF-TOKEN"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}