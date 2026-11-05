package com.gesstock.gesstock.config;

import com.gesstock.gesstock.interfaceweb.InterfaceWeb;
import com.gesstock.gesstock.security.LimiteConnexionsFilter;
import com.gesstock.gesstock.security.LimiteurConnexions;
import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnResource;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

import static com.gesstock.gesstock.config.Roles.ADMIN;
import static com.gesstock.gesstock.config.Roles.CAISSIER;
import static com.gesstock.gesstock.config.Roles.GESTIONNAIRE;
import static com.gesstock.gesstock.config.Roles.MANAGER;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;

/**
 * API pure : session par cookie, CSRF par cookie lu par le front, réponses d'erreur en JSON, aucune page HTML.
 * Principe : tout ce qui n'est pas explicitement autorisé est refusé ({@code anyRequest().denyAll()}).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final List<String> allowedOrigins;
    private final boolean secureCookies;

    public SecurityConfig(
        @Value("${app.cors.allowed-origins}") List<String> allowedOrigins,
        @Value("${app.security.cookies.secure:true}") boolean secureCookies
    ) {
        this.allowedOrigins = allowedOrigins;
        this.secureCookies = secureCookies;
    }

    /**
     * Supervision (port séparé, jamais exposé au réseau) : seule la sonde de santé est ouverte, sans session ni cookie.
     * Sans cette chaîne, la règle « tout refuser par défaut » ci-dessous bloquerait aussi le contrôle de santé de Docker.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain supervision(HttpSecurity http) throws Exception {
        http
            .securityMatcher(EndpointRequest.toAnyEndpoint())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(EndpointRequest.to("health")).permitAll()
                .anyRequest().denyAll())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .csrf(csrf -> csrf.disable());
        return http.build();
    }

    /**
     * Interface embarquée (voir {@link InterfaceWeb}) : la page d'entrée, ses fichiers et ses pages, en LECTURE SEULE (GET) et
     * pour tout le monde — c'est ce qui permet d'afficher l'écran de connexion. Aucun cookie, aucune session : ces requêtes ne
     * touchent à rien. Le reste (l'API surtout) tombe dans la chaîne suivante et reste soumis à ses règles. Cette chaîne
     * n'existe pas tant que l'interface n'est pas dans le programme : développement et tests refusent alors ces chemins comme avant.
     */
    @Bean
    @Order(2)
    @ConditionalOnResource(resources = InterfaceWeb.CONDITION_INDEX)
    public SecurityFilterChain interfaceWeb(HttpSecurity http) throws Exception {
        String[] chemins = java.util.stream.Stream.concat(java.util.Arrays.stream(InterfaceWeb.PAGES), java.util.Arrays.stream(InterfaceWeb.FICHIERS))
            .toArray(String[]::new);
        http
            .securityMatchers(correspondance -> correspondance.requestMatchers(GET, chemins))
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .csrf(csrf -> csrf.disable());
        return http.build();
    }

    @Bean
    @Order(3)
    public SecurityFilterChain filterChain(HttpSecurity http, ApiSecurityHandlers handlers, LimiteurConnexions limiteur) throws Exception {
        CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepository.setCookieCustomizer(cookie -> cookie.sameSite("Lax").secure(secureCookies));

        http
            // Authentification par cookie de session : la protection CSRF reste active. Le jeton est exposé dans le
            // cookie XSRF-TOKEN, relu par le frontend (axios) puis renvoyé dans l'en-tête X-XSRF-TOKEN.
            .csrf(csrf -> csrf
                .csrfTokenRepository(csrfRepository)
                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
            .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
            .addFilterBefore(new LimiteConnexionsFilter(limiteur), UsernamePasswordAuthenticationFilter.class)
            .cors(Customizer.withDefaults())
            // Pas de mémorisation de la requête refusée : une requête anonyme ne crée plus de session serveur.
            .requestCache(cache -> cache.requestCache(new NullRequestCache()))
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint(handlers)
                .accessDeniedHandler(handlers))
            .authorizeHttpRequests(auth -> auth
                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                .requestMatchers(POST, "/api/auth/login").permitAll()

                // Documentation de l'API : jamais anonyme (et désactivée hors profil « dev » par la configuration).
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").hasRole(ADMIN)

                .requestMatchers("/api/auth/**").authenticated()
                .requestMatchers("/api/referentiels/utilisateurs/**", "/api/referentiels/roles/**").hasRole(ADMIN)

                // Import d'articles (et le catalogue modifiable qui porte les prix d'achat) : réservé au stock, y compris en lecture.
                .requestMatchers("/api/articles/import/**").hasAnyRole(ADMIN, MANAGER, GESTIONNAIRE)
                // Catalogue : lecture pour tous les métiers (la caisse en a besoin), écriture pour le stock.
                .requestMatchers(GET, "/api/articles/**", "/api/referentiels/unites-mesure/**")
                    .hasAnyRole(ADMIN, MANAGER, GESTIONNAIRE, CAISSIER)
                .requestMatchers("/api/articles/**", "/api/referentiels/unites-mesure/**")
                    .hasAnyRole(ADMIN, MANAGER, GESTIONNAIRE)
                // Un avoir saisi à la main efface une dette sans qu'aucun argent ne bouge : décision de responsable.
                .requestMatchers(POST, "/api/fournisseurs/*/avoirs").hasAnyRole(ADMIN, MANAGER)
                .requestMatchers("/api/fournisseurs/**", "/api/approvisionnements/**")
                    .hasAnyRole(ADMIN, MANAGER, GESTIONNAIRE)
                // Un retour au fournisseur sort du stock et crée un avoir : le préparer est un travail de stock, le valider une décision de responsable.
                .requestMatchers(POST, "/api/retours-fournisseurs/*/validation").hasAnyRole(ADMIN, MANAGER)
                .requestMatchers("/api/retours-fournisseurs/**").hasAnyRole(ADMIN, MANAGER, GESTIONNAIRE)
                // Inventaire : le comptage est un travail de stock ; appliquer les écarts au stock est une décision de responsable.
                .requestMatchers(POST, "/api/inventaires/*/validation", "/api/inventaires/*/annulation", "/api/inventaires/*/reprise")
                    .hasAnyRole(ADMIN, MANAGER)
                .requestMatchers("/api/inventaires/**").hasAnyRole(ADMIN, MANAGER, GESTIONNAIRE)

                // Caisse et ventes. L'annulation d'une vente (retour de stock, dette, remboursement) est réservée
                // aux responsables : elle peut faire sortir de l'argent de la caisse.
                // Le journal COMPLET de la journée montre les lignes de tous les caissiers : réservé aux responsables, comme la liste
                // des comptes dont on peut demander le journal. Un caissier lit le sien (/api/caisse/journal/caissier), que le
                // contrôleur limite à son propre nom.
                .requestMatchers(GET, "/api/caisse/journal", "/api/caisse/journal/excel", "/api/caisse/journal/pdf", "/api/caisse/journal/caissiers")
                    .hasAnyRole(ADMIN, MANAGER, GESTIONNAIRE)
                // Un apport de fonds augmente les espèces attendues en caisse : une entrée fictive pourrait cacher un manque.
                .requestMatchers(POST, "/api/caisse/mouvements/entrees").hasAnyRole(ADMIN, MANAGER)
                .requestMatchers("/api/referentiels/sessions-caisse/**", "/api/caisse/**")
                    .hasAnyRole(ADMIN, MANAGER, GESTIONNAIRE, CAISSIER)
                .requestMatchers(POST, "/api/ventes/*/annulation").hasAnyRole(ADMIN, MANAGER)
                .requestMatchers("/api/proformas/**", "/api/ventes/**")
                    .hasAnyRole(ADMIN, MANAGER, GESTIONNAIRE, CAISSIER)
                .requestMatchers("/api/clients/**", "/api/dettes-clients/**")
                    .hasAnyRole(ADMIN, MANAGER, GESTIONNAIRE, CAISSIER)

                // Entreprise (nom, coordonnées, logo imprimés sur les documents) : tout le monde les lit, car un caissier
                // imprime des tickets ; les modifier est une décision de responsable.
                .requestMatchers(GET, "/api/entreprise", "/api/entreprise/logo").hasAnyRole(ADMIN, MANAGER, GESTIONNAIRE, CAISSIER)
                .requestMatchers("/api/entreprise/**").hasAnyRole(ADMIN, MANAGER)

                // Pilotage.
                .requestMatchers(GET, "/api/promotions/**").hasAnyRole(ADMIN, MANAGER, GESTIONNAIRE, CAISSIER)
                .requestMatchers("/api/promotions/**", "/api/statistiques/**", "/api/journal-activites/**")
                    .hasAnyRole(ADMIN, MANAGER, GESTIONNAIRE)

                .anyRequest().denyAll()
            )
            // Connexion par formulaire (POST /api/auth/login) mais sans page HTML : le « loginPage » personnalisé
            // supprime la page de connexion générée par Spring ; les réponses sont du JSON, jamais des redirections.
            .formLogin(form -> form
                .loginPage("/api/auth/login")
                .successHandler(handlers::loginReussi)
                .failureHandler(handlers::loginEchoue))
            .logout(logout -> logout
                .logoutUrl("/api/auth/logout")
                .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)));

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of(
            HttpMethod.GET.name(), HttpMethod.POST.name(), HttpMethod.PUT.name(),
            HttpMethod.PATCH.name(), HttpMethod.DELETE.name(), HttpMethod.OPTIONS.name()));
        configuration.setAllowedHeaders(List.of("Content-Type", "Accept", "X-XSRF-TOKEN", "X-Request-Id"));
        configuration.setExposedHeaders(List.of("X-Request-Id", "Content-Disposition", "X-Total-Count"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
