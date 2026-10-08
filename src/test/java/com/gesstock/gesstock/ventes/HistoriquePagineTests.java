package com.gesstock.gesstock.ventes;

import com.gesstock.gesstock.clients.dto.ClientRequest;
import com.gesstock.gesstock.clients.dto.ClientResponse;
import com.gesstock.gesstock.clients.service.ClientCatalogueService;
import com.gesstock.gesstock.entities.Article;
import com.gesstock.gesstock.entities.ModeReglement;
import com.gesstock.gesstock.referentiels.dto.OuvertureSessionRequest;
import com.gesstock.gesstock.referentiels.service.SessionCaisseService;
import com.gesstock.gesstock.repositories.ArticleRepository;
import com.gesstock.gesstock.ventes.dto.LigneDocumentRequest;
import com.gesstock.gesstock.ventes.dto.PageVentesResponse;
import com.gesstock.gesstock.ventes.dto.VenteDirecteRequest;
import com.gesstock.gesstock.ventes.dto.VenteResponse;
import com.gesstock.gesstock.ventes.service.GestionVentesService;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * L'historique ne se charge jamais en entier : il est paginé (les plus récentes d'abord), la taille est bornée, et une
 * page se charge en quelques requêtes quel que soit le nombre de lignes de ventes.
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Transactional
class HistoriquePagineTests {

    private static final String ADMIN = "admin";

    @Autowired
    private GestionVentesService ventesService;
    @Autowired
    private ClientCatalogueService clientService;
    @Autowired
    private SessionCaisseService sessionService;
    @Autowired
    private ArticleRepository articleRepository;
    @Autowired
    private EntityManager entityManager;

    private void vendre(Long clientId, int nombre) {
        Article riz = articleRepository.findByReferenceIgnoreCase("ALIM-001").orElseThrow();
        Article huile = articleRepository.findByReferenceIgnoreCase("ALIM-002").orElseThrow();
        for (int i = 0; i < nombre; i++) {
            VenteResponse brouillon = ventesService.creerVenteDirecte(new VenteDirecteRequest(clientId, false,
                List.of(new LigneDocumentRequest(riz.getId(), null, 1, null, null),
                        new LigneDocumentRequest(huile.getId(), null, 1, null, null)),
                BigDecimal.ZERO, ModeReglement.ESPECES, new BigDecimal("10000")), ADMIN);
            ventesService.validerVente(brouillon.id(), ADMIN);
        }
    }

    @Test
    void lHistoriqueEstPagineLesPlusRecentesDAbordEtLaTailleEstBornee() {
        sessionService.ouvrir(new OuvertureSessionRequest(null, new BigDecimal("1000")), ADMIN);
        vendre(null, 7);

        PageVentesResponse premiere = ventesService.listerVentesPaginees(null, 0, 3);
        PageVentesResponse deuxieme = ventesService.listerVentesPaginees(null, 1, 3);

        assertThat(premiere.ventes()).hasSize(3);
        assertThat(premiere.total()).isEqualTo(7);
        assertThat(premiere.ventes()).isSortedAccordingTo(Comparator.comparing(VenteResponse::id).reversed());
        assertThat(deuxieme.ventes()).hasSize(3);
        assertThat(deuxieme.ventes().get(0).id()).as("la page 2 reprend après la page 1")
            .isLessThan(premiere.ventes().get(2).id());

        // valeurs absurdes : page négative ramenée à 0, taille ramenée à la limite, taille nulle ramenée à 1
        assertThat(ventesService.listerVentesPaginees(null, -5, 10_000).ventes()).hasSize(7);
        assertThat(ventesService.listerVentesPaginees(null, 0, 0).ventes()).hasSize(1);
    }

    @Test
    void l_historiqueDUnClientNeContientQueSesVentesEtLeCompteurClientLesCompte() {
        sessionService.ouvrir(new OuvertureSessionRequest(null, new BigDecimal("1000")), ADMIN);
        ClientResponse client = clientService.creer(new ClientRequest("Client pagination", null, null, null));
        vendre(client.id(), 5);
        vendre(null, 2);

        PageVentesResponse page = ventesService.listerVentesPaginees(client.id(), 1, 2);

        assertThat(page.total()).isEqualTo(5);
        assertThat(page.ventes()).hasSize(2).allMatch(vente -> client.id().equals(vente.clientId()));
        assertThat(clientService.obtenir(client.id()).nombreAchats()).isEqualTo(5);
        assertThat(clientService.lister()).filteredOn(c -> c.id().equals(client.id()))
            .singleElement().extracting(ClientResponse::nombreAchats).isEqualTo(5L);
        assertThat(ventesService.listerVentesPaginees(987654321L, 0, 10).ventes()).isEmpty();
    }

    @Test
    void uneVenteBrouillonOuAnnuleeNEstPasCompteeCommeAchat() {
        sessionService.ouvrir(new OuvertureSessionRequest(null, new BigDecimal("1000")), ADMIN);
        ClientResponse client = clientService.creer(new ClientRequest("Client brouillon", null, null, null));
        Article riz = articleRepository.findByReferenceIgnoreCase("ALIM-001").orElseThrow();
        ventesService.creerVenteDirecte(new VenteDirecteRequest(client.id(), false,
            List.of(new LigneDocumentRequest(riz.getId(), null, 1, null, null)),
            BigDecimal.ZERO, ModeReglement.ESPECES, new BigDecimal("10000")), ADMIN); // reste en brouillon

        assertThat(clientService.obtenir(client.id()).nombreAchats()).isZero();
    }

    @Test
    void unePageDe25VentesDeDeuxLignesSeChargeEnQuelquesRequetes() {
        sessionService.ouvrir(new OuvertureSessionRequest(null, new BigDecimal("1000")), ADMIN);
        vendre(null, 25);
        entityManager.flush();
        entityManager.clear();
        SessionFactory fabrique = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class);
        fabrique.getStatistics().clear();

        PageVentesResponse page = ventesService.listerVentesPaginees(null, 0, 25);

        assertThat(page.ventes()).hasSize(25);
        // identifiants de la page + comptage + chargement des 25 ventes avec leurs lignes : indépendant du nombre de ventes
        assertThat(fabrique.getStatistics().getPrepareStatementCount()).as("requêtes SQL").isLessThanOrEqualTo(4);
    }
}
