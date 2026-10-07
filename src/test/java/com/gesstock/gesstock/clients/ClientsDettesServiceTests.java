package com.gesstock.gesstock.clients;

import com.gesstock.gesstock.clients.dto.ClientRequest;
import com.gesstock.gesstock.clients.dto.ClientResponse;
import com.gesstock.gesstock.clients.dto.DetteResponse;
import com.gesstock.gesstock.clients.dto.EncoursClientResponse;
import com.gesstock.gesstock.clients.dto.ReglementRequest;
import com.gesstock.gesstock.clients.dto.ReglementResponse;
import com.gesstock.gesstock.clients.exception.ClientIntrouvableException;
import com.gesstock.gesstock.clients.exception.OperationDetteInvalideException;
import com.gesstock.gesstock.clients.service.ClientCatalogueService;
import com.gesstock.gesstock.clients.service.GestionDetteClientService;
import com.gesstock.gesstock.entities.Article;
import com.gesstock.gesstock.entities.ModeReglement;
import com.gesstock.gesstock.entities.StatutDette;
import com.gesstock.gesstock.referentiels.dto.OuvertureSessionRequest;
import com.gesstock.gesstock.referentiels.service.SessionCaisseService;
import com.gesstock.gesstock.repositories.ArticleRepository;
import com.gesstock.gesstock.ventes.dto.LigneDocumentRequest;
import com.gesstock.gesstock.ventes.dto.VenteDirecteRequest;
import com.gesstock.gesstock.ventes.dto.VenteResponse;
import com.gesstock.gesstock.ventes.service.GestionVentesService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Les données de démonstration (admin, ELEC-001 à 245000, stock 12) viennent du DataSeeder. */
@SpringBootTest
@Transactional
class ClientsDettesServiceTests {

    private static final String ADMIN = "admin";

    @Autowired
    private ClientCatalogueService clientService;
    @Autowired
    private GestionDetteClientService detteService;
    @Autowired
    private GestionVentesService ventesService;
    @Autowired
    private SessionCaisseService sessionService;
    @Autowired
    private ArticleRepository articleRepository;
    @Autowired
    private EntityManager entityManager;

    private ClientResponse client(String nom) {
        return clientService.creer(new ClientRequest(nom, null, "0100000000", "Adresse"));
    }

    private Article telephone() {
        return articleRepository.findByReferenceIgnoreCase("ELEC-001").orElseThrow();
    }

    /** Vente à crédit validée de 2 téléphones (490 000), qui crée la dette du client. */
    private DetteResponse detteDe490000(ClientResponse client) {
        sessionService.ouvrir(new OuvertureSessionRequest(LocalDate.now(), new BigDecimal("1000")), ADMIN);
        VenteResponse brouillon = ventesService.creerVenteDirecte(new VenteDirecteRequest(
            client.id(), true,
            List.of(new LigneDocumentRequest(telephone().getId(), null, 2, null, null)),
            BigDecimal.ZERO, null, null), ADMIN);
        ventesService.validerVente(brouillon.id(), ADMIN);
        entityManager.flush();
        entityManager.clear();
        return detteService.listerDettesClient(client.id()).get(0);
    }

    // --- Fichier clients ---

    @Test
    void clientCrud() {
        ClientResponse cree = client("Client test");
        ClientResponse modifie = clientService.modifier(cree.id(),
            new ClientRequest("Client renommé", null, null, null));
        assertThat(modifie.nom()).isEqualTo("Client renommé");

        clientService.supprimer(cree.id());
        assertThatThrownBy(() -> clientService.obtenir(cree.id()))
            .isInstanceOf(ClientIntrouvableException.class);
    }

    // --- Ventes à crédit et dettes ---

    @Test
    void venteACreditCreeUneDetteEtAlimenteLEncours() {
        ClientResponse c = client("Client crédit");
        DetteResponse dette = detteDe490000(c);

        assertThat(dette.montantInitial()).isEqualByComparingTo("490000");
        assertThat(dette.solde()).isEqualByComparingTo("490000");
        assertThat(dette.statut()).isEqualTo(StatutDette.EN_COURS);

        EncoursClientResponse encours = clientService.obtenirEncours(c.id());
        assertThat(encours.montantDu()).isEqualByComparingTo("490000");
    }

    @Test
    void clientAvecDetteNePeutPasEtreSupprime() {
        ClientResponse c = client("Client endetté");
        detteDe490000(c);

        assertThatThrownBy(() -> clientService.supprimer(c.id()))
            .isInstanceOf(OperationDetteInvalideException.class);
    }

    // --- Règlements ---

    @Test
    void reglementsPartielsPuisTotalSoldentLaDetteAvecHistorique() {
        ClientResponse c = client("Client payeur");
        DetteResponse dette = detteDe490000(c);

        detteService.enregistrerReglement(dette.id(),
            new ReglementRequest(new BigDecimal("200000"), ModeReglement.ESPECES, null), ADMIN);
        DetteResponse partielle = detteService.obtenirDette(dette.id());
        assertThat(partielle.statut()).isEqualTo(StatutDette.PARTIELLEMENT_REGLEE);
        assertThat(partielle.solde()).isEqualByComparingTo("290000");

        ReglementResponse dernier = detteService.enregistrerReglement(dette.id(),
            new ReglementRequest(new BigDecimal("290000"), ModeReglement.ESPECES, "REF-1"), ADMIN);
        assertThat(dernier.recuPar()).isEqualTo(ADMIN);

        DetteResponse soldee = detteService.obtenirDette(dette.id());
        assertThat(soldee.statut()).isEqualTo(StatutDette.REGLEE);
        assertThat(soldee.solde()).isEqualByComparingTo("0");
        assertThat(detteService.listerReglementsDette(dette.id())).hasSize(2);
        assertThat(detteService.listerReglementsClient(c.id())).hasSize(2);
        assertThat(clientService.obtenirEncours(c.id()).montantDu()).isEqualByComparingTo("0");
    }

    @Test
    void reglementAuDessusDuSoldeOuSurDetteSoldeeEstRefuse() {
        DetteResponse dette = detteDe490000(client("Client excès"));

        assertThatThrownBy(() -> detteService.enregistrerReglement(dette.id(),
            new ReglementRequest(new BigDecimal("490001"), ModeReglement.ESPECES, null), ADMIN))
            .isInstanceOf(OperationDetteInvalideException.class);

        detteService.enregistrerReglement(dette.id(),
            new ReglementRequest(new BigDecimal("490000"), ModeReglement.ESPECES, null), ADMIN);
        assertThatThrownBy(() -> detteService.enregistrerReglement(dette.id(),
            new ReglementRequest(new BigDecimal("1"), ModeReglement.ESPECES, null), ADMIN))
            .isInstanceOf(OperationDetteInvalideException.class);
    }
}
