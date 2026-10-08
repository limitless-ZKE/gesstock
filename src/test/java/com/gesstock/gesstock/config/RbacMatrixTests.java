package com.gesstock.gesstock.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/**
 * Matrice des droits : pour CHAQUE route, quels rôles passent. Tout est refusé par défaut, donc un rôle oublié, un
 * ancien rôle (VENDEUR, USER) ou une nouvelle route non déclarée échoue ici. Ajouter une route = ajouter une ligne.
 * « Passe » = la sécurité laisse entrer (la réponse est alors 2xx, 400, 404 ou 409 selon le métier, jamais 401/403).
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RbacMatrixTests {

    private static final Set<String> TOUS = Set.of("ADMIN", "MANAGER", "GESTIONNAIRE", "CAISSIER");
    private static final Set<String> STOCK = Set.of("ADMIN", "MANAGER", "GESTIONNAIRE");
    private static final Set<String> DIRECTION = Set.of("ADMIN", "MANAGER");
    private static final Set<String> ADMIN = Set.of("ADMIN");
    /** Anciens rôles et rôle inconnu : ils ne doivent plus rien pouvoir faire. */
    private static final List<String> ROLES_SANS_DROIT = List.of("VENDEUR", "USER", "STAGIAIRE");
    private static final long INEXISTANT = 987654321L;

    private record Route(HttpMethod method, String url, String body, Set<String> autorises) {
        String libelle() {
            return method + " " + url;
        }
    }

    private static Route route(HttpMethod method, String url, String body, Set<String> autorises) {
        return new Route(method, url, body, autorises);
    }

    private static final List<Route> ROUTES = List.of(
        route(HttpMethod.GET, "/api/articles", null, TOUS),
        route(HttpMethod.POST, "/api/articles", "{}", STOCK),
        route(HttpMethod.DELETE, "/api/articles/" + INEXISTANT, null, STOCK),
        route(HttpMethod.POST, "/api/articles/" + INEXISTANT + "/ajustement-stock", "{}", STOCK),
        route(HttpMethod.GET, "/api/articles/categories", null, TOUS),
        route(HttpMethod.GET, "/api/articles/alertes-stock", null, TOUS),
        route(HttpMethod.GET, "/api/referentiels/unites-mesure", null, TOUS),
        route(HttpMethod.POST, "/api/referentiels/unites-mesure", "{}", STOCK),
        route(HttpMethod.GET, "/api/inventaires", null, STOCK),
        route(HttpMethod.POST, "/api/inventaires", "{}", STOCK),
        route(HttpMethod.PUT, "/api/inventaires/" + INEXISTANT + "/lignes/1", "{}", STOCK),
        route(HttpMethod.POST, "/api/inventaires/" + INEXISTANT + "/soumission", null, STOCK),
        route(HttpMethod.GET, "/api/inventaires/" + INEXISTANT + "/export.xlsx", null, STOCK),
        route(HttpMethod.POST, "/api/inventaires/" + INEXISTANT + "/validation", null, DIRECTION),
        route(HttpMethod.POST, "/api/inventaires/" + INEXISTANT + "/annulation", null, DIRECTION),
        route(HttpMethod.POST, "/api/inventaires/" + INEXISTANT + "/reprise", null, DIRECTION),
        route(HttpMethod.GET, "/api/fournisseurs", null, STOCK),
        route(HttpMethod.GET, "/api/approvisionnements", null, STOCK),
        route(HttpMethod.POST, "/api/approvisionnements", "{}", STOCK),
        route(HttpMethod.GET, "/api/clients", null, TOUS),
        route(HttpMethod.POST, "/api/clients", "{}", TOUS),
        route(HttpMethod.DELETE, "/api/clients/" + INEXISTANT, null, TOUS),
        route(HttpMethod.GET, "/api/dettes-clients", null, TOUS),
        route(HttpMethod.GET, "/api/ventes", null, TOUS),
        route(HttpMethod.POST, "/api/ventes", "{}", TOUS),
        route(HttpMethod.POST, "/api/ventes/" + INEXISTANT + "/annulation", "{\"motif\":\"x\"}", DIRECTION),
        route(HttpMethod.GET, "/api/proformas", null, TOUS),
        route(HttpMethod.GET, "/api/promotions", null, TOUS),
        route(HttpMethod.POST, "/api/promotions", "{}", STOCK),
        route(HttpMethod.GET, "/api/statistiques/dashboard", null, STOCK),
        route(HttpMethod.GET, "/api/journal-activites", null, STOCK),
        route(HttpMethod.GET, "/api/referentiels/utilisateurs", null, ADMIN),
        route(HttpMethod.GET, "/api/referentiels/roles", null, ADMIN),
        route(HttpMethod.POST, "/api/referentiels/sessions-caisse/ouverture", "{}", TOUS),
        route(HttpMethod.GET, "/v3/api-docs", null, ADMIN)
    );

    @Autowired
    private MockMvc mockMvc;

    private int statut(Route route, String role) throws Exception {
        var builder = request(route.method(), route.url());
        builder.with(csrf());
        if (route.body() != null) {
            builder.contentType(MediaType.APPLICATION_JSON).characterEncoding("UTF-8").content(route.body());
        }
        if (role != null) {
            builder.with(user("sonde-" + role).roles(role));
        }
        return mockMvc.perform(builder).andReturn().getResponse().getStatus();
    }

    @Test
    void chaqueRoleNAccedeQuAuxRoutesQuiLuiSontOuvertes() throws Exception {
        List<String> ecarts = new ArrayList<>();
        for (Route route : ROUTES) {
            for (String role : List.of("ADMIN", "MANAGER", "GESTIONNAIRE", "CAISSIER")) {
                int statut = statut(route, role);
                boolean passe = statut != 401 && statut != 403;
                if (passe != route.autorises().contains(role)) {
                    ecarts.add(route.libelle() + " pour " + role + " : HTTP " + statut
                        + (route.autorises().contains(role) ? " (devrait passer)" : " (devrait être refusé)"));
                }
            }
            for (String role : ROLES_SANS_DROIT) {
                int statut = statut(route, role);
                if (statut != 403) {
                    ecarts.add(route.libelle() + " pour l'ancien rôle " + role + " : HTTP " + statut + " (devrait être 403)");
                }
            }
            int anonyme = statut(route, null);
            if (anonyme != 401) {
                ecarts.add(route.libelle() + " sans connexion : HTTP " + anonyme + " (devrait être 401)");
            }
        }
        assertThat(ecarts).as("écarts avec la matrice des droits").isEmpty();
    }

    @Test
    void uneRouteNonDeclareeEstRefuseeMemeAuxAdministrateurs() throws Exception {
        // refus par défaut : y compris les routes de l'ancienne interface web, qui n'existent plus
        for (String url : List.of("/api/route-oubliee", "/clients/supprimer/1", "/export/ventes/excel", "/articles/supprimer/1",
            "/swagger-ui/index.html", "/actuator/env")) {
            var resultat = mockMvc.perform(request(HttpMethod.GET, url).with(user("a").roles("MANAGER")).with(csrf()))
                .andReturn().getResponse();
            assertThat(resultat.getStatus()).as(url).isIn(403, 404);
        }
    }

    @Test
    void lAuthentificationSeuleSuffitPourConnaitreSonPropreProfil() throws Exception {
        for (String role : List.of("ADMIN", "MANAGER", "GESTIONNAIRE", "CAISSIER", "VENDEUR", "USER")) {
            var resultat = mockMvc.perform(request(HttpMethod.GET, "/api/auth/me").with(user("x").roles(role)))
                .andReturn().getResponse();
            assertThat(resultat.getStatus()).as("GET /api/auth/me pour " + role).isEqualTo(200);
        }
    }
}
