package com.gesstock.gesstock.ventes;

import com.gesstock.gesstock.entities.Article;
import com.gesstock.gesstock.repositories.ArticleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Règles de saisie des ventes et des journées vues de l'extérieur (JSON, paramètres de requête, rôles). */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
// csrf() remplace le dépôt de jetons du filtre dans le contexte partagé : on isole donc ce contexte.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class VentesHttpTests {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ArticleRepository articleRepository;

    private String vente(Long articleId, String prix) {
        return "{\"aCredit\":false,\"tauxTva\":0,\"modeReglement\":\"ESPECES\",\"montantRecu\":1000000,"
            + "\"lignes\":[{\"articleId\":" + articleId + ",\"quantite\":1,\"prixUnitaire\":" + prix + "}]}";
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder envoyer(String url, String json) {
        return post(url).contentType(MediaType.APPLICATION_JSON).characterEncoding("UTF-8").content(json)
            .with(user("admin").roles("ADMIN")).with(csrf());
    }

    @Test
    void prixNegocieAvecPlusDeDeuxDecimalesEstRefuse() throws Exception {
        Article telephone = articleRepository.findByReferenceIgnoreCase("ELEC-001").orElseThrow();

        mockMvc.perform(envoyer("/api/ventes", vente(telephone.getId(), "220000.123")))
            .andExpect(status().isBadRequest());
        mockMvc.perform(envoyer("/api/ventes", vente(telephone.getId(), "220000.12")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.lignes[0].prixUnitaire").value(220000.12))
            .andExpect(jsonPath("$.lignes[0].prixCatalogue").value(245000));
    }

    @Test
    void prixNegocieAuDessusDuCatalogueEstRefuseAvecUnMessageClair() throws Exception {
        Article telephone = articleRepository.findByReferenceIgnoreCase("ELEC-001").orElseThrow();

        mockMvc.perform(envoyer("/api/ventes", vente(telephone.getId(), "300000")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("prix catalogue")));
    }

    @Test
    void unPrixNegocieSousLePrixDAchatRefuseLaValidationAvecUnCodeStable() throws Exception {
        Article telephone = articleRepository.findByReferenceIgnoreCase("ELEC-001").orElseThrow();
        mockMvc.perform(envoyer("/api/referentiels/sessions-caisse/ouverture", "{\"fondDeCaisse\":1000}"))
            .andExpect(status().is2xxSuccessful());
        // Le brouillon s'enregistre (le coût n'est connu qu'à la validation)...
        String brouillon = mockMvc.perform(envoyer("/api/ventes", vente(telephone.getId(), "150000")))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        Number id = com.jayway.jsonpath.JsonPath.read(brouillon, "$.id");

        // ... mais ne se valide pas : coût moyen de 189 000 au démarrage des données de démonstration.
        mockMvc.perform(envoyer("/api/ventes/" + id + "/validation", ""))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("PRIX_SOUS_LE_COUT"))
            .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("minimum 189000")));
    }

    @Test
    void historiqueSeFiltreParDateEtRefuseUneDateInvalide() throws Exception {
        mockMvc.perform(get("/api/ventes").param("date", "2000-01-01").with(user("caissier").roles("CAISSIER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(get("/api/ventes").param("date", LocalDate.now().toString()).with(user("caissier").roles("CAISSIER")))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/ventes").param("date", "pas-une-date").with(user("caissier").roles("CAISSIER")))
            .andExpect(status().isBadRequest());
    }

    @Test
    void laDateDeLaJourneeNeSeChoisitPasParLApi() throws Exception {
        mockMvc.perform(envoyer("/api/referentiels/sessions-caisse/ouverture",
                "{\"journee\":\"" + LocalDate.now().plusDays(5) + "\",\"fondDeCaisse\":1000}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("fixée par le serveur")));
    }
}
