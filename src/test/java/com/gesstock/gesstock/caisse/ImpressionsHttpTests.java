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

/** Contrat HTTP de l'historique des ventes et du journal de caisse par période : droits, paramètres, fichiers produits. */
@SpringBootTest
@AutoConfigureMockMvc
class ImpressionsHttpTests {

    private static final String[] CHEMINS = {"/api/ventes/historique", "/api/caisse/journal"};

    @Autowired
    private MockMvc mockMvc;

    private static String aujourdhui() {
        return LocalDate.now().toString();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "MANAGER", "GESTIONNAIRE"})
    void lesResponsablesConsultentLesDeuxEtats(String role) throws Exception {
        for (String chemin : CHEMINS) {
            mockMvc.perform(get(chemin).param("debut", aujourdhui()).param("fin", aujourdhui()).with(user("u").roles(role)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.debut").value(aujourdhui()))
                .andExpect(jsonPath("$.fin").value(aujourdhui()));
        }
    }

    /** Le journal complet montre les lignes de tous les caissiers : un caissier lit le sien (voir JournalCaissierHttpTests). */
    @Test
    void unCaissierLitLHistoriqueDesVentesMaisPasLeJournalCompletDeLaCaisse() throws Exception {
        mockMvc.perform(get("/api/ventes/historique").param("debut", aujourdhui()).param("fin", aujourdhui()).with(user("u").roles("CAISSIER")))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/caisse/journal").param("debut", aujourdhui()).param("fin", aujourdhui()).with(user("u").roles("CAISSIER")))
            .andExpect(status().isForbidden());
    }

    @Test
    void sansConnexionLesEtatsSontRefuses() throws Exception {
        for (String chemin : CHEMINS) {
            mockMvc.perform(get(chemin).param("debut", aujourdhui()).param("fin", aujourdhui())).andExpect(status().isUnauthorized());
            mockMvc.perform(get(chemin + "/pdf").param("debut", aujourdhui()).param("fin", aujourdhui())).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void lHistoriqueDesVentesNEstPasPrisPourUnNumeroDeVente() throws Exception {
        mockMvc.perform(get("/api/ventes/historique").param("debut", aujourdhui()).param("fin", aujourdhui()).with(user("u").roles("CAISSIER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.resume.nombreValidees").isNumber())
            .andExpect(jsonPath("$.ventes").isArray());
    }

    @Test
    void lePdfEtLExcelSontTelechargeablesAvecUnNomParlant() throws Exception {
        String periode = aujourdhui() + "-" + aujourdhui();
        var pdf = mockMvc.perform(get("/api/ventes/historique/pdf").param("debut", aujourdhui()).param("fin", aujourdhui()).with(user("u").roles("CAISSIER")))
            .andExpect(status().isOk())
            .andExpect(content().contentType("application/pdf"))
            .andExpect(header().string("Content-Disposition", "attachment; filename=gesstock-historique-ventes-" + periode + ".pdf"))
            .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");

        var excel = mockMvc.perform(get("/api/caisse/journal/excel").param("debut", aujourdhui()).param("fin", aujourdhui()).with(user("u").roles("GESTIONNAIRE")))
            .andExpect(status().isOk())
            .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .andExpect(header().string("Content-Disposition", "attachment; filename=gesstock-journal-caisse-" + periode + ".xlsx"))
            .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(excel, 0, 2)).as("un classeur .xlsx est une archive zip").isEqualTo("PK");

        mockMvc.perform(get("/api/caisse/journal/pdf").param("debut", aujourdhui()).param("fin", aujourdhui()).with(user("u").roles("MANAGER")))
            .andExpect(status().isOk()).andExpect(content().contentType("application/pdf"));
        mockMvc.perform(get("/api/ventes/historique/excel").param("debut", aujourdhui()).param("fin", aujourdhui()).with(user("u").roles("ADMIN")))
            .andExpect(status().isOk());
    }

    @Test
    void uneDateAbsenteOuMalFormeeEstRefuseeSansPlanterLeServeur() throws Exception {
        for (String chemin : CHEMINS) {
            mockMvc.perform(get(chemin).param("debut", aujourdhui()).with(user("u").roles("ADMIN")))
                .andExpect(status().isBadRequest());
            mockMvc.perform(get(chemin).param("debut", "pas-une-date").param("fin", aujourdhui()).with(user("u").roles("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PARAMETRE_INVALIDE"));
        }
    }

    @Test
    void unePeriodeIncoherenteOuTropLongueEstRefuseeEnFrancais() throws Exception {
        for (String chemin : CHEMINS) {
            for (String variante : new String[] {"", "/pdf", "/excel"}) {
                mockMvc.perform(get(chemin + variante).param("debut", aujourdhui()).param("fin", LocalDate.now().minusDays(1).toString())
                        .with(user("u").roles("ADMIN")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("REQUETE_INVALIDE"))
                    .andExpect(jsonPath("$.detail").value("La date de fin doit être égale ou postérieure à la date de début."));
            }
            mockMvc.perform(get(chemin).param("debut", LocalDate.now().minusDays(500).toString()).param("fin", aujourdhui())
                    .with(user("u").roles("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("La période ne peut pas dépasser 366 jours."));
        }
    }
}
