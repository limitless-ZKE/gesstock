package com.gesstock.gesstock.ventes;

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
 * Droits des trois historiques (toutes les ventes, ventes soldées, règlements de dettes) et de leurs documents. Un caissier ne voit
 * que les siens : le serveur impose son nom, un autre nom est refusé (403). Un responsable voit tout, ou le vendeur qu'il nomme.
 */
@SpringBootTest
@AutoConfigureMockMvc
class HistoriquesParVendeurHttpTests {

    private static final String[] ETATS = {"/api/ventes/historique", "/api/ventes/historique/soldees", "/api/ventes/historique/reglements"};

    @Autowired
    private MockMvc mockMvc;

    private static String aujourdhui() {
        return LocalDate.now().toString();
    }

    @Test
    void unCaissierVoitSesHistoriquesEtPeutSeNommerMaisPasNommerUnAutre() throws Exception {
        for (String etat : ETATS) {
            for (String suite : new String[] {"", "/pdf", "/excel"}) {
                mockMvc.perform(get(etat + suite).param("debut", aujourdhui()).param("fin", aujourdhui()).with(user("caissier").roles("CAISSIER")))
                    .andExpect(status().isOk());
                mockMvc.perform(get(etat + suite).param("debut", aujourdhui()).param("fin", aujourdhui()).param("vendeur", "Caissier")
                        .with(user("caissier").roles("CAISSIER")))
                    .andExpect(status().isOk());
                mockMvc.perform(get(etat + suite).param("debut", aujourdhui()).param("fin", aujourdhui()).param("vendeur", "admin")
                        .with(user("caissier").roles("CAISSIER")))
                    .andExpect(status().isForbidden());
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "MANAGER", "GESTIONNAIRE"})
    void unResponsableVoitToutOuLeVendeurQuIlNomme(String role) throws Exception {
        for (String etat : ETATS) {
            mockMvc.perform(get(etat).param("debut", aujourdhui()).param("fin", aujourdhui()).with(user("u").roles(role)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.debut").value(aujourdhui()))
                .andExpect(jsonPath("$.resume").exists());
            mockMvc.perform(get(etat).param("debut", aujourdhui()).param("fin", aujourdhui()).param("vendeur", "caissier").with(user("u").roles(role)))
                .andExpect(status().isOk());
        }
    }

    @Test
    void lesFichiersPortentLeNomDuVendeurQuandIlNYEnAQuUn() throws Exception {
        String periode = aujourdhui() + "-" + aujourdhui();
        var pdf = mockMvc.perform(get("/api/ventes/historique/soldees/pdf").param("debut", aujourdhui()).param("fin", aujourdhui())
                .with(user("caissier").roles("CAISSIER")))
            .andExpect(status().isOk())
            .andExpect(content().contentType("application/pdf"))
            .andExpect(header().string("Content-Disposition", "attachment; filename=gesstock-ventes-soldees-caissier-" + periode + ".pdf"))
            .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");

        var excel = mockMvc.perform(get("/api/ventes/historique/reglements/excel").param("debut", aujourdhui()).param("fin", aujourdhui())
                .with(user("u").roles("MANAGER")))
            .andExpect(status().isOk())
            .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .andExpect(header().string("Content-Disposition", "attachment; filename=gesstock-reglements-dettes-" + periode + ".xlsx"))
            .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(excel, 0, 2)).isEqualTo("PK");

        mockMvc.perform(get("/api/ventes/historique/reglements/pdf").param("debut", aujourdhui()).param("fin", aujourdhui()).param("vendeur", "caissier")
                .with(user("u").roles("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", "attachment; filename=gesstock-reglements-dettes-caissier-" + periode + ".pdf"));
    }

    @Test
    void sansConnexionOnNeVoitRien() throws Exception {
        for (String etat : ETATS) {
            for (String suite : new String[] {"", "/pdf", "/excel"}) {
                mockMvc.perform(get(etat + suite).param("debut", aujourdhui()).param("fin", aujourdhui())).andExpect(status().isUnauthorized());
            }
        }
    }

    @Test
    void unePeriodeIncoherenteEstRefuseeEnFrancais() throws Exception {
        for (String etat : ETATS) {
            mockMvc.perform(get(etat).param("debut", aujourdhui()).param("fin", LocalDate.now().minusDays(1).toString()).with(user("u").roles("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("La date de fin doit être égale ou postérieure à la date de début."));
        }
    }
}
