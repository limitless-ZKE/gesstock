package com.gesstock.gesstock.interfaceweb;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * L'interface embarquée dans le programme : une seule adresse pour tous les postes du réseau. Des fichiers factices tiennent
 * lieu de l'interface construite ; sans eux (développement, autres tests) tout ceci est inactif — voir AuthFlowTests.
 */
@SpringBootTest(properties = {
    "app.interface.index=file:target/ui-test/index.html",
    "spring.web.resources.static-locations=file:target/ui-test/"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class InterfaceEmbarqueeTests {

    private static final String MARQUEUR = "INTERFACE-GESSTOCK-FACTICE";

    static {
        try {
            Path dossier = Path.of("target", "ui-test");
            Files.createDirectories(dossier.resolve("assets"));
            Files.writeString(dossier.resolve("index.html"), "<!doctype html><html><body>" + MARQUEUR + "</body></html>");
            Files.writeString(dossier.resolve("favicon.svg"), "<svg xmlns=\"http://www.w3.org/2000/svg\"/>");
            Files.writeString(dossier.resolve("assets").resolve("index-AbC123.js"), "console.log('gesstock');");
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void lAdresseRacineEtChaquePageOuvrentLApplicationSansConnexionNiCookie() throws Exception {
        for (String page : InterfaceWeb.PAGES) {
            // MockMvc n'exécute pas les redirections internes : il enregistre la destination. Le contenu réellement servi
            // est vérifié ci-dessous pour /index.html et, de bout en bout, sur un vrai serveur.
            mockMvc.perform(get(page))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
        }
    }

    @Test
    void laPageDEntreeContientLInterface() throws Exception {
        mockMvc.perform(get("/index.html"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString(MARQUEUR)))
            .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    void laPageDEntreeNEstJamaisMiseEnCacheCarEllePointeVersLesFichiersAJour() throws Exception {
        MvcResult resultat = mockMvc.perform(get("/index.html")).andExpect(status().isOk()).andReturn();

        assertThat(resultat.getResponse().getHeader(HttpHeaders.CACHE_CONTROL)).contains("no-store");
    }

    @Test
    void lesFichiersAEmpreinteSontGardesUnAnParLeNavigateur() throws Exception {
        mockMvc.perform(get("/assets/index-AbC123.js"))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("max-age=31536000")))
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("immutable")));
        mockMvc.perform(get("/favicon.svg")).andExpect(status().isOk());
    }

    @Test
    void unFichierQuiNExistePasDonneUne404EtNonLaPageDEntree() throws Exception {
        mockMvc.perform(get("/assets/absent.js")).andExpect(status().isNotFound());
    }

    @Test
    void lApiResteProtegeeEtNeRenvoieJamaisLInterface() throws Exception {
        mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/route-inconnue")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/entreprise")).andExpect(status().isUnauthorized());

        // même connecté en administrateur, une route d'API inconnue est REFUSÉE (« tout est refusé par défaut ») :
        // jamais la page de l'interface avec un code 200
        mockMvc.perform(get("/api/route-inconnue").with(user("u").roles("ADMIN")))
            .andExpect(status().isForbidden())
            .andExpect(forwardedUrl(null))
            .andExpect(content().string(org.hamcrest.Matchers.not(containsString(MARQUEUR))));
    }

    @Test
    void toutCeQuiNEstPasLInterfaceNiLApiResteRefuse() throws Exception {
        for (String chemin : List.of("/clients/supprimer/1", "/export/ventes/excel", "/swagger-ui/index.html", "/v3/api-docs", "/actuator/env", "/pas-une-page")) {
            mockMvc.perform(get(chemin)).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void lInterfaceNEstQueEnLectureSeule() throws Exception {
        mockMvc.perform(post("/dashboard").with(csrf()).with(user("u").roles("ADMIN"))).andExpect(status().is4xxClientError());
        mockMvc.perform(post("/index.html")).andExpect(status().is4xxClientError());
    }

    /**
     * Garde-fou : la liste des pages servies par le serveur doit rester celle du routeur du frontend. Une page ajoutée à
     * App.tsx sans l'être dans InterfaceWeb.PAGES afficherait une erreur au premier rechargement (F5) ; l'inverse ouvrirait
     * une adresse qui ne mène nulle part.
     */
    @Test
    void lesPagesServiesSontExactementLesRoutesDuFrontend() throws IOException {
        Path app = Path.of("frontend", "src", "App.tsx");
        String source = Files.readString(app);
        Matcher routes = Pattern.compile("path=\"(/[^\"*]*)\"").matcher(source);
        List<String> duFront = new ArrayList<>();
        while (routes.find()) {
            duFront.add(routes.group(1));
        }
        duFront.add("/");   // la racine redirige vers l'écran d'accueil du rôle

        assertThat(duFront).as("routes lues dans " + app).hasSizeGreaterThan(10);
        assertThat(InterfaceWeb.PAGES).containsExactlyInAnyOrderElementsOf(duFront);
    }
}
