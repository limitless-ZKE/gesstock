package com.gesstock.gesstock.ventes;

import com.gesstock.gesstock.repositories.ArticleRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Ce que voit le front quand un accord est nécessaire : un 409 au code stable, avec le motif en français. */
@SpringBootTest
@AutoConfigureMockMvc
class ApprobationRemisesHttpTests {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ArticleRepository articleRepository;

    private String vente(String prixNegocie, String approbation) {
        Long articleId = articleRepository.findByReferenceIgnoreCase("ELEC-001").orElseThrow().getId();
        return "{\"aCredit\":false,\"tauxTva\":0,\"modeReglement\":\"ESPECES\",\"montantRecu\":10000000,"
            + "\"lignes\":[{\"articleId\":" + articleId + ",\"quantite\":1,\"prixUnitaire\":" + prixNegocie + "}]"
            + (approbation == null ? "" : ",\"approbation\":" + approbation) + "}";
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder caissier(String corps) {
        Cookie jeton = new Cookie("XSRF-TOKEN", "jeton-de-test-0123456789");
        return post("/api/ventes").with(user("caissier").roles("CAISSIER")).cookie(jeton)
            .header("X-XSRF-TOKEN", jeton.getValue()).contentType(MediaType.APPLICATION_JSON).characterEncoding("UTF-8").content(corps);
    }

    @Test
    void uneRemiseAuDelaDuPlafondDonneUn409AvecLeCodeApprobationRequise() throws Exception {
        mockMvc.perform(caissier(vente("196000", null)))
            .andExpect(status().isConflict())
            .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
            .andExpect(jsonPath("$.code").value("APPROBATION_REQUISE"))
            .andExpect(jsonPath("$.detail").value(containsString("ELEC-001")));
    }

    @Test
    void unAccordRefuseDonneUn409AvecLeCodeApprobationRefusee() throws Exception {
        mockMvc.perform(caissier(vente("196000", "{\"username\":\"inconnu\",\"motDePasse\":\"faux\"}")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("APPROBATION_REFUSEE"))
            .andExpect(jsonPath("$.detail").value(containsString("Accord refusé")));
    }
}
