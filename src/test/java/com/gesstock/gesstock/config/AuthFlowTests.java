package com.gesstock.gesstock.config;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Parcours d'authentification de l'API pure, joué comme le fait le front : on lit le cookie XSRF-TOKEN puis on le
 * renvoie dans l'en-tête X-XSRF-TOKEN. (On n'utilise volontairement pas le post-processeur csrf() de MockMvc : il remplace
 * le dépôt de jetons par une version stockée en session, ce qui fausserait les vérifications sur les sessions.)
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthFlowTests {

    @Autowired
    private MockMvc mockMvc;

    private String jetonCsrf() throws Exception {
        Cookie cookie = mockMvc.perform(get("/api/auth/me")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        assertThat(cookie).as("cookie XSRF-TOKEN émis dès la première réponse").isNotNull();
        return cookie.getValue();
    }

    private MockHttpServletRequestBuilder avecJeton(MockHttpServletRequestBuilder requete, String jeton) {
        return requete.cookie(new Cookie("XSRF-TOKEN", jeton)).header("X-XSRF-TOKEN", jeton);
    }

    private MockHttpSession seConnecter(String utilisateur, String motDePasse) throws Exception {
        MvcResult connexion = mockMvc.perform(avecJeton(post("/api/auth/login"), jetonCsrf())
                .param("username", utilisateur).param("password", motDePasse))
            .andExpect(status().isNoContent())
            .andReturn();
        return (MockHttpSession) connexion.getRequest().getSession(false);
    }

    @Test
    void uneRequeteAnonymeEstRefuseeEnJsonSansCreerDeSession() throws Exception {
        MvcResult resultat = mockMvc.perform(get("/api/auth/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
            .andExpect(jsonPath("$.code").value("NON_AUTHENTIFIE"))
            .andReturn();

        assertThat(resultat.getRequest().getSession(false)).as("aucune session serveur pour un anonyme").isNull();
        assertThat(resultat.getResponse().getHeaders("Set-Cookie")).noneMatch(valeur -> valeur.startsWith("JSESSIONID"));
    }

    @Test
    void leCookieCsrfEstEmisSecurisePuisLisibleParLeFront() throws Exception {
        Cookie cookie = mockMvc.perform(get("/api/auth/me")).andReturn().getResponse().getCookie("XSRF-TOKEN");

        assertThat(cookie).isNotNull();
        assertThat(cookie.getSecure()).as("Secure").isTrue();
        assertThat(cookie.getAttribute("SameSite")).as("SameSite").isEqualTo("Lax");
        assertThat(cookie.isHttpOnly()).as("lisible par le front").isFalse();
    }

    @Test
    void connexionReussieDonneUneSessionEtDeconnexionLaDetruit() throws Exception {
        MockHttpSession session = seConnecter("admin", "admin123");
        assertThat(session).isNotNull();

        mockMvc.perform(get("/api/auth/me").session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("admin"))
            .andExpect(jsonPath("$.roles[0]").value("ADMIN"));

        mockMvc.perform(avecJeton(post("/api/auth/logout"), jetonCsrf()).session(session))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/auth/me").session(session))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void mauvaisMotDePasseDonneUn401JsonSansDireLaCause() throws Exception {
        for (String[] essai : new String[][] {{"admin", "mauvais"}, {"inconnu", "admin123"}}) {
            MvcResult resultat = mockMvc.perform(avecJeton(post("/api/auth/login"), jetonCsrf())
                    .param("username", essai[0]).param("password", essai[1]))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("IDENTIFIANTS_INVALIDES"))
                .andExpect(jsonPath("$.detail").value("Identifiant ou mot de passe incorrect"))
                .andReturn();
            assertThat(resultat.getRequest().getSession(false)).isNull();
        }
    }

    @Test
    void connexionSansJetonCsrfEstRefuseeAvecUnCodeExplicite() throws Exception {
        mockMvc.perform(post("/api/auth/login").param("username", "admin").param("password", "admin123"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("CSRF_INVALIDE"));
        // jeton présent dans l'en-tête mais différent du cookie : refusé aussi
        mockMvc.perform(post("/api/auth/login").cookie(new Cookie("XSRF-TOKEN", "a")).header("X-XSRF-TOKEN", "b")
                .param("username", "admin").param("password", "admin123"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("CSRF_INVALIDE"));
    }

    @Test
    void ilNExistePlusAucunePageHtmlDeConnexion() throws Exception {
        for (String url : List.of("/login", "/logout", "/", "/index.html", "/dashboard")) {
            MvcResult resultat = mockMvc.perform(get(url)).andReturn();
            assertThat(resultat.getResponse().getStatus()).as(url).isEqualTo(401);
            assertThat(resultat.getResponse().getContentType()).as(url).doesNotContain("html");
        }
    }

    @Test
    void laDocumentationDeLApiNEstJamaisAnonymeEtEstDesactiveParDefaut() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isUnauthorized());
        // même un administrateur connecté n'y accède pas : springdoc est désactivé hors profil « dev »
        mockMvc.perform(get("/v3/api-docs").with(SecurityMockMvcRequestPostProcessors.user("a").roles("ADMIN")))
            .andExpect(status().isNotFound());
    }
}
