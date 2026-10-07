package com.gesstock.gesstock.ventes;

import com.gesstock.gesstock.clients.dto.ClientRequest;
import com.gesstock.gesstock.clients.dto.ClientResponse;
import com.gesstock.gesstock.clients.dto.DetteResponse;
import com.gesstock.gesstock.clients.dto.ReglementRequest;
import com.gesstock.gesstock.clients.service.ClientCatalogueService;
import com.gesstock.gesstock.clients.service.GestionDetteClientService;
import com.gesstock.gesstock.entities.Article;
import com.gesstock.gesstock.entities.ModeReglement;
import com.gesstock.gesstock.referentiels.dto.OuvertureSessionRequest;
import com.gesstock.gesstock.referentiels.service.SessionCaisseService;
import com.gesstock.gesstock.repositories.ArticleRepository;
import com.gesstock.gesstock.ventes.dto.LigneDocumentRequest;
import com.gesstock.gesstock.ventes.dto.VenteDirecteRequest;
import com.gesstock.gesstock.ventes.dto.VenteResponse;
import com.gesstock.gesstock.ventes.service.GestionVentesService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** L'annulation d'une vente à crédit vue de l'extérieur : JSON réel, rôles et protection CSRF. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
// csrf() remplace le dépôt de jetons du filtre dans le contexte partagé : on isole donc ce contexte.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AnnulationVenteHttpTests {

    private static final String ADMIN = "admin";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private GestionVentesService ventesService;
    @Autowired
    private GestionDetteClientService detteService;
    @Autowired
    private ClientCatalogueService clientService;
    @Autowired
    private SessionCaisseService sessionService;
    @Autowired
    private ArticleRepository articleRepository;

    private VenteResponse venteACreditValidee(ClientResponse client) {
        Article telephone = articleRepository.findByReferenceIgnoreCase("ELEC-001").orElseThrow();
        VenteResponse brouillon = ventesService.creerVenteDirecte(new VenteDirecteRequest(
            client.id(), true,
            List.of(new LigneDocumentRequest(telephone.getId(), null, 1, null, null)),
            BigDecimal.ZERO, null, null), ADMIN);
        return ventesService.validerVente(brouillon.id(), ADMIN);
    }

    private ClientResponse clientAvecSessionOuverte() {
        sessionService.ouvrir(new OuvertureSessionRequest(LocalDate.now(), new BigDecimal("1000")), ADMIN);
        return clientService.creer(new ClientRequest("Client HTTP", null, "0100000000", "Adresse"));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder annuler(Long venteId, String json) {
        return post("/api/ventes/" + venteId + "/annulation")
            .contentType(MediaType.APPLICATION_JSON)
            .characterEncoding("UTF-8")
            .content(json);
    }

    @Test
    void annulationSansLeIndicateurDArticlesRapportesEstAcceptee() throws Exception {
        VenteResponse vente = venteACreditValidee(clientAvecSessionOuverte());

        // le champ articlesRetournesEnEtat est facultatif : absent, il vaut « false »
        mockMvc.perform(annuler(vente.id(), "{\"motif\":\"Erreur de saisie\"}")
                .with(user(ADMIN).roles("ADMIN")).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.vente.statut").value("ANNULEE"))
            .andExpect(jsonPath("$.montantRembourse").value(0));
    }

    @Test
    void clientPayeEstRembourseSeulementSiLesArticlesSontRapportes() throws Exception {
        ClientResponse client = clientAvecSessionOuverte();
        VenteResponse vente = venteACreditValidee(client);
        DetteResponse dette = detteService.listerDettesClient(client.id()).get(0);
        detteService.enregistrerReglement(dette.id(),
            new ReglementRequest(new BigDecimal("100000"), ModeReglement.ESPECES, null), ADMIN);

        // motif accentué, indicateur absent : refusé, rien ne change
        mockMvc.perform(annuler(vente.id(), "{\"motif\":\"Client mécontent\"}")
                .with(user(ADMIN).roles("ADMIN")).with(csrf()))
            .andExpect(status().isConflict());

        mockMvc.perform(annuler(vente.id(), "{\"motif\":\"Client mécontent\",\"articlesRetournesEnEtat\":true}")
                .with(user(ADMIN).roles("ADMIN")).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.vente.statut").value("ANNULEE"))
            .andExpect(jsonPath("$.montantRembourse").value(100000))
            .andExpect(jsonPath("$.remboursementEspeces").value(100000))
            .andExpect(jsonPath("$.remboursementAutresModes").value(0));
    }

    @Test
    void seuleLaDirectionPeutAnnulerEtLeMotifEstObligatoire() throws Exception {
        VenteResponse vente = venteACreditValidee(clientAvecSessionOuverte());

        mockMvc.perform(annuler(vente.id(), "{\"motif\":\"Test\"}")
                .with(user("caissier").roles("CAISSIER")).with(csrf()))
            .andExpect(status().isForbidden());
        mockMvc.perform(annuler(vente.id(), "{\"motif\":\"Test\"}")
                .with(user("vendeur").roles("VENDEUR")).with(csrf()))
            .andExpect(status().isForbidden());
        mockMvc.perform(annuler(vente.id(), "{\"motif\":\"  \"}")
                .with(user(ADMIN).roles("ADMIN")).with(csrf()))
            .andExpect(status().isBadRequest());
        mockMvc.perform(annuler(vente.id(), "{\"motif\":\"Test\"}")
                .with(user(ADMIN).roles("ADMIN")))   // sans jeton CSRF
            .andExpect(status().isForbidden());
    }
}
