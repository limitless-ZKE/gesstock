package com.gesstock.gesstock.clients;

import com.gesstock.gesstock.caisse.dto.MouvementCaisseRequest;
import com.gesstock.gesstock.caisse.service.MouvementCaisseService;
import com.gesstock.gesstock.clients.dto.ClientRequest;
import com.gesstock.gesstock.clients.dto.ClientResponse;
import com.gesstock.gesstock.clients.dto.DetteAvecClientResponse;
import com.gesstock.gesstock.clients.dto.DetteResponse;
import com.gesstock.gesstock.clients.dto.ReglementRequest;
import com.gesstock.gesstock.clients.dto.ReglementResponse;
import com.gesstock.gesstock.clients.service.ClientCatalogueService;
import com.gesstock.gesstock.clients.service.GestionDetteClientService;
import com.gesstock.gesstock.entities.Article;
import com.gesstock.gesstock.entities.ModeReglement;
import com.gesstock.gesstock.entities.StatutDette;
import com.gesstock.gesstock.entities.StatutProforma;
import com.gesstock.gesstock.referentiels.dto.OuvertureSessionRequest;
import com.gesstock.gesstock.referentiels.dto.SessionCaisseResponse;
import com.gesstock.gesstock.referentiels.service.SessionCaisseService;
import com.gesstock.gesstock.repositories.ArticleRepository;
import com.gesstock.gesstock.statistiques.dto.RapportStatistiquesResponse;
import com.gesstock.gesstock.statistiques.dto.SessionCaisseRapportResponse;
import com.gesstock.gesstock.statistiques.service.StatistiquesExportService;
import com.gesstock.gesstock.statistiques.service.StatistiquesService;
import com.gesstock.gesstock.ventes.dto.LigneDocumentRequest;
import com.gesstock.gesstock.ventes.dto.ProformaRequest;
import com.gesstock.gesstock.ventes.dto.ProformaResponse;
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

/** Les données de démonstration (admin, ELEC-001 à 245000, stock 12) viennent du DataSeeder. */
@SpringBootTest
@Transactional
class CaisseRapportEtDettesTests {

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
    private MouvementCaisseService mouvementService;
    @Autowired
    private StatistiquesService statistiquesService;
    @Autowired
    private StatistiquesExportService exportService;
    @Autowired
    private ArticleRepository articleRepository;
    @Autowired
    private EntityManager entityManager;

    private Article telephone() {
        return articleRepository.findByReferenceIgnoreCase("ELEC-001").orElseThrow();
    }

    private ClientResponse client(String nom) {
        return clientService.creer(new ClientRequest(nom, null, "0100000000", "Adresse"));
    }

    private SessionCaisseResponse ouvrirSession() {
        return sessionService.ouvrir(new OuvertureSessionRequest(LocalDate.now(), new BigDecimal("1000")), ADMIN);
    }

    private List<LigneDocumentRequest> unTelephone(int quantite) {
        return List.of(new LigneDocumentRequest(telephone().getId(), null, BigDecimal.valueOf(quantite), null, null));
    }

    /** Vente à crédit validée de 2 téléphones (490 000) : crée la dette du client. */
    private DetteResponse detteDe490000(ClientResponse client) {
        VenteResponse brouillon = ventesService.creerVenteDirecte(new VenteDirecteRequest(
            client.id(), true, unTelephone(2), BigDecimal.ZERO, null, null), ADMIN);
        ventesService.validerVente(brouillon.id(), ADMIN);
        entityManager.flush();
        entityManager.clear();
        return detteService.listerDettesClient(client.id()).get(0);
    }

    // --- Règlements hors espèces ---

    @Test
    void reglementParCarteOuVirementNeNecessitePasDeSessionEtNEntrePasDansLaCaisse() {
        ouvrirSession();
        DetteResponse dette = detteDe490000(client("Client carte"));

        ReglementResponse carte = detteService.enregistrerReglement(dette.id(),
            new ReglementRequest(new BigDecimal("100000"), ModeReglement.CARTE, "TPE-1"), ADMIN);
        ReglementResponse virement = detteService.enregistrerReglement(dette.id(),
            new ReglementRequest(new BigDecimal("50000"), ModeReglement.VIREMENT, null), ADMIN);

        assertThat(carte.modeReglement()).isEqualTo(ModeReglement.CARTE);
        assertThat(virement.modeReglement()).isEqualTo(ModeReglement.VIREMENT);
        assertThat(detteService.obtenirDette(dette.id()).solde()).isEqualByComparingTo("340000");

        // seuls les règlements en espèces alimentent le solde théorique de la caisse
        SessionCaisseResponse session = sessionService.sessionOuverte(LocalDate.now());
        assertThat(session.reglementsClientsEspeces()).isEqualByComparingTo("0");
    }

    // --- Vente à crédit issue d'un proforma ---

    @Test
    void conversionDUnProformaACreditCreeLaDetteDuClient() {
        ouvrirSession();
        ClientResponse c = client("Client proforma crédit");
        ProformaResponse proforma = ventesService.creerProforma(new ProformaRequest(
            "PF-CRED-1", LocalDate.now(), LocalDate.now().plusDays(7), c.id(), unTelephone(1)), ADMIN);
        ventesService.modifierStatutProforma(proforma.id(), StatutProforma.ENVOYEE, ADMIN);
        ventesService.modifierStatutProforma(proforma.id(), StatutProforma.ACCEPTEE, ADMIN);

        VenteResponse vente = ventesService.convertirProformaEnVente(proforma.id(), true, null, null, ADMIN);
        entityManager.flush();
        entityManager.clear();

        assertThat(vente.aCredit()).isTrue();
        List<DetteResponse> dettes = detteService.listerDettesClient(c.id());
        assertThat(dettes).hasSize(1);
        assertThat(dettes.get(0).montantInitial()).isEqualByComparingTo("245000");
        assertThat(dettes.get(0).statut()).isEqualTo(StatutDette.EN_COURS);
    }

    // --- Liste de toutes les dettes en un appel ---

    @Test
    void listeDeToutesLesDettesContientLeNomDuClient() {
        ouvrirSession();
        ClientResponse c = client("Client liste");
        DetteResponse dette = detteDe490000(c);

        List<DetteAvecClientResponse> toutes = detteService.listerToutesLesDettes();

        assertThat(toutes).anySatisfy(d -> {
            assertThat(d.id()).isEqualTo(dette.id());
            assertThat(d.clientId()).isEqualTo(c.id());
            assertThat(d.client()).isEqualTo("Client liste");
            assertThat(d.solde()).isEqualByComparingTo("490000");
        });
    }

    // --- Rapport : entrées et sorties de caisse par session ---

    @Test
    void rapportDetailleSortiesEtReglementsEnEspecesParSession() throws java.io.IOException {
        SessionCaisseResponse session = ouvrirSession();
        DetteResponse dette = detteDe490000(client("Client rapport"));
        detteService.enregistrerReglement(dette.id(),
            new ReglementRequest(new BigDecimal("30000"), ModeReglement.ESPECES, null), ADMIN);
        mouvementService.enregistrer(new MouvementCaisseRequest("Achat fournitures", "Papeterie", new BigDecimal("200")), ADMIN);

        RapportStatistiquesResponse rapport = statistiquesService.rapport(LocalDate.now(), LocalDate.now());

        SessionCaisseRapportResponse ligne = rapport.sessionsCaisse().stream()
            .filter(s -> s.sessionId().equals(session.id())).findFirst().orElseThrow();
        assertThat(ligne.reglementsClientsEspeces()).isEqualByComparingTo("30000");
        assertThat(ligne.sortiesCaisse()).isEqualByComparingTo("200");

        // les exports Excel et PDF se génèrent avec ces nouvelles colonnes
        assertThat(exportService.exporterExcel(rapport)).isNotEmpty();
        assertThat(exportService.exporterPdf(rapport)).isNotEmpty();
    }
}
