package com.gesstock.gesstock.common;

import com.gesstock.gesstock.clients.service.ClientCatalogueService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import jakarta.servlet.http.Cookie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrat d'erreur unique : TOUTES les réponses d'erreur de l'API sont en {@code application/problem+json} avec un
 * {@code code} stable et un {@code requestId}. Une erreur imprévue ne fuit aucun détail technique.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ErrorContractTests {

    private static final String SECRET = "mot-de-passe-de-la-base=hunter2";

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private ClientCatalogueService clientService;

    private Cookie jeton = new Cookie("XSRF-TOKEN", "jeton-de-test-0123456789");

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder admin(
        org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder requete
    ) {
        return requete.with(user("admin").roles("ADMIN")).cookie(jeton).header("X-XSRF-TOKEN", jeton.getValue());
    }

    @Test
    void uneErreurImprevueDonneUn500GeneriqueSansAucuneFuite() throws Exception {
        when(clientService.lister()).thenThrow(new IllegalStateException(SECRET));

        MvcResult resultat = mockMvc.perform(admin(get("/api/clients")))
            .andExpect(status().isInternalServerError())
            .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
            .andExpect(jsonPath("$.code").value("ERREUR_INTERNE"))
            .andExpect(jsonPath("$.requestId").isNotEmpty())
            .andReturn();

        String corps = resultat.getResponse().getContentAsString();
        assertThat(corps).doesNotContain(SECRET).doesNotContain("IllegalStateException").doesNotContain("at com.gesstock");
        // l'identifiant du corps est celui de l'en-tête : le support retrouve la trace dans les logs
        assertThat(corps).contains(resultat.getResponse().getHeader("X-Request-Id"));
    }

    @Test
    void lesErreursStandardsSontToutesAuMemeFormat() throws Exception {
        mockMvc.perform(admin(get("/api/ventes/987654321")))
            .andExpect(status().isNotFound())
            .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
            .andExpect(jsonPath("$.code").value("RESSOURCE_INTROUVABLE"));

        mockMvc.perform(admin(delete("/api/ventes")))
            .andExpect(status().isMethodNotAllowed())
            .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
            .andExpect(jsonPath("$.code").value("METHODE_NON_AUTORISEE"));

        mockMvc.perform(admin(post("/api/clients").contentType(MediaType.TEXT_PLAIN).content("x")))
            .andExpect(status().isUnsupportedMediaType())
            .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
            .andExpect(jsonPath("$.code").value("TYPE_NON_SUPPORTE"));

        mockMvc.perform(admin(post("/api/clients").contentType(MediaType.APPLICATION_JSON).content("{\"nom\":")))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
            .andExpect(jsonPath("$.code").value("CORPS_INVALIDE"));

        mockMvc.perform(admin(get("/api/ventes/abc")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("PARAMETRE_INVALIDE"));

        mockMvc.perform(admin(get("/api/statistiques/rapport").param("debut", "2026-02-01").param("fin", "2026-01-01")))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @Test
    void laValidationDonneLesErreursParChampEnFrancais() throws Exception {
        mockMvc.perform(admin(post("/api/clients").contentType(MediaType.APPLICATION_JSON).content("{}")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION"))
            .andExpect(jsonPath("$.erreurs.nom").value(containsString("vide")))
            .andExpect(header().exists("X-Request-Id"));
    }
}
