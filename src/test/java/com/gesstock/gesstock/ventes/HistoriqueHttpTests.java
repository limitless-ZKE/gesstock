package com.gesstock.gesstock.ventes;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Contrat HTTP de la liste des ventes : paginée, avec le total dans un en-tête que le navigateur peut lire. */
@SpringBootTest
@AutoConfigureMockMvc
class HistoriqueHttpTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void sansDateLHistoriqueEstPagineEtLeTotalEstDansUnEnTete() throws Exception {
        mockMvc.perform(get("/api/ventes").param("size", "2").with(user("admin").roles("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(header().exists("X-Total-Count"))
            .andExpect(jsonPath("$.length()").value(lessThanOrEqualTo(2)));
    }

    @Test
    void uneTailleAbsurdeEstBorneeSansErreur() throws Exception {
        mockMvc.perform(get("/api/ventes").param("size", "100000").param("page", "-3").with(user("admin").roles("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(lessThanOrEqualTo(200)));
    }

    @Test
    void avecUneDateLesVentesDeLaJourneeSontRenduesAvecLeurNombre() throws Exception {
        mockMvc.perform(get("/api/ventes").param("date", LocalDate.now().toString()).with(user("admin").roles("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(header().exists("X-Total-Count"));
    }

    @Test
    void unClientIdNonNumeriqueDonneUneErreurDeParametre() throws Exception {
        mockMvc.perform(get("/api/ventes").param("clientId", "abc").with(user("admin").roles("ADMIN")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("PARAMETRE_INVALIDE"));
    }
}
