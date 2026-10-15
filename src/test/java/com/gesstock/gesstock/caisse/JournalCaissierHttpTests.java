package com.gesstock.gesstock.caisse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Droits du journal de caisse. Le journal COMPLET montre les lignes de tous les caissiers : il est réservé aux responsables. Un
 * caissier lit et imprime le sien, et seulement le sien (le serveur impose son nom, quoi que l'écran envoie) ; un responsable
 * choisit le caissier.
 */
@SpringBootTest
@AutoConfigureMockMvc
class JournalCaissierHttpTests {

    private static final String JOURNAL = "/api/caisse/journal";
    private static final String CAISSIER_CHEMIN = "/api/caisse/journal/caissier";

    @Autowired
    private MockMvc mockMvc;

    private static String aujourdhui() {
        return LocalDate.now().toString();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "/excel", "/pdf", "/caissiers"})
    void unCaissierNePeutPasLireLeJournalCompletNiLaListeDesCaissiers(String suite) throws Exception {
        mockMvc.perform(get(JOURNAL + suite).param("debut", aujourdhui()).param("fin", aujourdhui()).with(user("caissier").roles("CAISSIER")))
            .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "MANAGER", "GESTIONNAIRE"})
    void lesResponsablesLisentLeJournalCompletEtLaListeDesCaissiers(String role) throws Exception {
        mockMvc.perform(get(JOURNAL).param("debut", aujourdhui()).param("fin", aujourdhui()).with(user("u").roles(role)))
            .andExpect(status().isOk());
        mockMvc.perform(get(JOURNAL + "/caissiers").with(user("u").roles(role)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.username=='caissier')]").exists())
            .andExpect(jsonPath("$[?(@.username=='admin')]").exists());
    }

    @Test
    void unCaissierLitSonPropreJournalSansRienPreciser() throws Exception {
        mockMvc.perform(get(CAISSIER_CHEMIN).param("debut", aujourdhui()).param("fin", aujourdhui()).with(user("caissier").roles("CAISSIER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.caissier").value("caissier"))
            .andExpect(jsonPath("$.debut").value(aujourdhui()))
            .andExpect(jsonPath("$.jours").isArray());
    }

    @Test
    void unCaissierPeutNommerLuiMemeMaisPasUnAutre() throws Exception {
        mockMvc.perform(get(CAISSIER_CHEMIN).param("debut", aujourdhui()).param("fin", aujourdhui()).param("caissier", "caissier")
                .with(user("caissier").roles("CAISSIER")))
            .andExpect(status().isOk());
        for (String suite : new String[] {"", "/pdf", "/excel"}) {
            mockMvc.perform(get(CAISSIER_CHEMIN + suite).param("debut", aujourdhui()).param("fin", aujourdhui()).param("caissier", "admin")
                    .with(user("caissier").roles("CAISSIER")))
                .andExpect(status().isForbidden());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "MANAGER", "GESTIONNAIRE"})
    void unResponsableChoisitLeCaissierEtDoitLeNommer(String role) throws Exception {
        mockMvc.perform(get(CAISSIER_CHEMIN).param("debut", aujourdhui()).param("fin", aujourdhui()).param("caissier", "caissier").with(user("u").roles(role)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.caissier").value("caissier"));
        mockMvc.perform(get(CAISSIER_CHEMIN).param("debut", aujourdhui()).param("fin", aujourdhui()).with(user("u").roles(role)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("REQUETE_INVALIDE"))
            .andExpect(jsonPath("$.detail").value("Choisissez le caissier dont vous voulez le journal."));
        mockMvc.perform(get(CAISSIER_CHEMIN).param("debut", aujourdhui()).param("fin", aujourdhui()).param("caissier", "personne").with(user("u").roles(role)))
            .andExpect(status().isNotFound());
    }

    @Test
    void lePdfEtLExcelDuCaissierSontTelechargeablesAvecSonNomDansLeFichier() throws Exception {
        String periode = aujourdhui() + "-" + aujourdhui();
        var pdf = mockMvc.perform(get(CAISSIER_CHEMIN + "/pdf").param("debut", aujourdhui()).param("fin", aujourdhui()).with(user("caissier").roles("CAISSIER")))
            .andExpect(status().isOk())
            .andExpect(content().contentType("application/pdf"))
            .andExpect(header().string("Content-Disposition", "attachment; filename=gesstock-journal-caissier-caissier-" + periode + ".pdf"))
            .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");

        var excel = mockMvc.perform(get(CAISSIER_CHEMIN + "/excel").param("debut", aujourdhui()).param("fin", aujourdhui()).param("caissier", "caissier")
                .with(user("u").roles("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .andExpect(header().string("Content-Disposition", "attachment; filename=gesstock-journal-caissier-caissier-" + periode + ".xlsx"))
            .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(excel, 0, 2)).isEqualTo("PK");
    }

    @Test
    void sansConnexionOnNeLitRien() throws Exception {
        for (String chemin : new String[] {CAISSIER_CHEMIN, CAISSIER_CHEMIN + "/pdf", CAISSIER_CHEMIN + "/excel", JOURNAL + "/caissiers"}) {
            mockMvc.perform(get(chemin).param("debut", aujourdhui()).param("fin", aujourdhui())).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void unePeriodeIncoherenteEstRefuseeEnFrancais() throws Exception {
        for (String suite : new String[] {"", "/pdf", "/excel"}) {
            mockMvc.perform(get(CAISSIER_CHEMIN + suite).param("debut", aujourdhui()).param("fin", LocalDate.now().minusDays(1).toString())
                    .with(user("caissier").roles("CAISSIER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("La date de fin doit être égale ou postérieure à la date de début."));
        }
    }
}
