package com.gesstock.gesstock.journal;

import com.gesstock.gesstock.journal.dto.OperationJournal;
import com.gesstock.gesstock.journal.service.JournalOperationsService;
import com.gesstock.gesstock.journal.service.OperationsConnues.Famille;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Le journal des opérations par HTTP : réservé aux responsables (décision de l'utilisateur), fichiers au nom parlant, et les
 * connexions réelles (réussie, mot de passe faux, identifiant inconnu) y entrent avec le poste d'où elles viennent.
 */
@SpringBootTest
@AutoConfigureMockMvc
class JournalOperationsHttpTests {

    private static final String JOURNAL = "/api/journal-activites/operations";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JournalOperationsService operationsService;

    private static String aujourdhui() {
        return LocalDate.now().toString();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "/pdf", "/excel"})
    void unCaissierNAPasAccesAuJournalDesOperations(String suite) throws Exception {
        mockMvc.perform(get(JOURNAL + suite).param("debut", aujourdhui()).param("fin", aujourdhui()).with(user("caissier").roles("CAISSIER")))
            .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "MANAGER", "GESTIONNAIRE"})
    void unResponsableLitLeJournalDeTousOuDUnUtilisateur(String role) throws Exception {
        mockMvc.perform(get(JOURNAL).param("debut", aujourdhui()).param("fin", aujourdhui()).with(user("u").roles(role)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nombre").isNumber())
            .andExpect(jsonPath("$.operations").isArray());
        mockMvc.perform(get(JOURNAL).param("debut", aujourdhui()).param("fin", aujourdhui()).param("utilisateur", "caissier")
                .param("famille", "VENTES").with(user("u").roles(role)))
            .andExpect(status().isOk());
    }

    @Test
    void lesFichiersPortentLeNomDeLUtilisateurQuandIlNYEnAQuUn() throws Exception {
        String periode = aujourdhui() + "-" + aujourdhui();
        var pdf = mockMvc.perform(get(JOURNAL + "/pdf").param("debut", aujourdhui()).param("fin", aujourdhui()).param("utilisateur", "caissier")
                .with(user("u").roles("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(content().contentType("application/pdf"))
            .andExpect(header().string("Content-Disposition", "attachment; filename=gesstock-journal-operations-caissier-" + periode + ".pdf"))
            .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");

        var excel = mockMvc.perform(get(JOURNAL + "/excel").param("debut", aujourdhui()).param("fin", aujourdhui()).with(user("u").roles("MANAGER")))
            .andExpect(status().isOk())
            .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .andExpect(header().string("Content-Disposition", "attachment; filename=gesstock-journal-operations-" + periode + ".xlsx"))
            .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(excel, 0, 2)).isEqualTo("PK");
    }

    @Test
    void uneFamilleInconnueEtUnePeriodeIncoherenteSontRefusees() throws Exception {
        mockMvc.perform(get(JOURNAL).param("debut", aujourdhui()).param("fin", aujourdhui()).param("famille", "PAS_UNE_FAMILLE")
                .with(user("u").roles("ADMIN")))
            .andExpect(status().isBadRequest());
        mockMvc.perform(get(JOURNAL).param("debut", aujourdhui()).param("fin", LocalDate.now().minusDays(1).toString()).with(user("u").roles("ADMIN")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("La date de fin doit être égale ou postérieure à la date de début."));
    }

    @Test
    void sansConnexionOnNeLitRien() throws Exception {
        for (String suite : new String[] {"", "/pdf", "/excel"}) {
            mockMvc.perform(get(JOURNAL + suite).param("debut", aujourdhui()).param("fin", aujourdhui())).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void lesConnexionsEntrentDansLeJournalAvecLePoste() throws Exception {
        String jeton = mockMvc.perform(get("/api/auth/me")).andReturn().getResponse().getCookie("XSRF-TOKEN").getValue();
        mockMvc.perform(avecJeton(post("/api/auth/login"), jeton).param("username", "gestionnaire").param("password", "gestionnaire123"))
            .andExpect(status().isNoContent());
        mockMvc.perform(avecJeton(post("/api/auth/login"), jeton).param("username", "gestionnaire").param("password", "pas-le-bon"))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(avecJeton(post("/api/auth/login"), jeton).param("username", "inconnu-du-journal").param("password", "x"))
            .andExpect(status().isUnauthorized());

        List<OperationJournal> duGestionnaire = operationsService.journal(LocalDate.now(), LocalDate.now(), "gestionnaire", Famille.COMPTES).operations();
        assertThat(duGestionnaire).anySatisfy(operation -> {
            assertThat(operation.operation()).isEqualTo("Connexion");
            assertThat(operation.details()).isEqualTo("Connexion du compte gestionnaire, depuis le poste 127.0.0.1");
            assertThat(operation.poste()).isEqualTo("127.0.0.1");
        });
        assertThat(duGestionnaire).anySatisfy(operation -> {
            assertThat(operation.operation()).isEqualTo("Connexion refusée");
            assertThat(operation.details()).isEqualTo("Connexion refusée pour le compte gestionnaire (mot de passe incorrect ou compte inconnu), "
                + "depuis le poste 127.0.0.1");
        });
        assertThat(operationsService.journal(LocalDate.now(), LocalDate.now(), null, Famille.COMPTES).operations())
            .anySatisfy(operation -> {
                assertThat(operation.utilisateur()).isNull();
                assertThat(operation.details()).contains("identifiant inconnu « inconnu-du-journal »");
            });
    }

    private static MockHttpServletRequestBuilder avecJeton(MockHttpServletRequestBuilder requete, String jeton) {
        return requete.cookie(new Cookie("XSRF-TOKEN", jeton)).header("X-XSRF-TOKEN", jeton);
    }
}
