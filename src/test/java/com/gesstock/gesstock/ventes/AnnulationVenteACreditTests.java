package com.gesstock.gesstock.ventes;

import com.gesstock.gesstock.caisse.dto.MouvementCaisseRequest;
import com.gesstock.gesstock.caisse.service.MouvementCaisseService;
import com.gesstock.gesstock.clients.dto.ClientRequest;
import com.gesstock.gesstock.clients.dto.ClientResponse;
import com.gesstock.gesstock.clients.dto.DetteResponse;
import com.gesstock.gesstock.clients.dto.ReglementRequest;
import com.gesstock.gesstock.clients.exception.OperationDetteInvalideException;
import com.gesstock.gesstock.clients.service.ClientCatalogueService;
import com.gesstock.gesstock.clients.service.GestionDetteClientService;
import com.gesstock.gesstock.common.exception.ConflitRessourceException;
import com.gesstock.gesstock.entities.Article;
import com.gesstock.gesstock.entities.ModeReglement;
import com.gesstock.gesstock.entities.MouvementStock;
import com.gesstock.gesstock.entities.StatutDette;
import com.gesstock.gesstock.entities.StatutVente;
import com.gesstock.gesstock.entities.TypeMouvementStock;
import com.gesstock.gesstock.journal.dto.JournalActiviteResponse;
import com.gesstock.gesstock.journal.service.JournalActiviteService;
import com.gesstock.gesstock.referentiels.dto.OuvertureSessionRequest;
import com.gesstock.gesstock.referentiels.dto.SessionCaisseResponse;
import com.gesstock.gesstock.referentiels.service.SessionCaisseService;
import com.gesstock.gesstock.repositories.ArticleRepository;
import com.gesstock.gesstock.repositories.MouvementStockRepository;
import com.gesstock.gesstock.repositories.VenteRepository;
import com.gesstock.gesstock.ventes.dto.AnnulationVenteResponse;
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
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Les données de démonstration (admin, ELEC-001 à 245000, stock 12) viennent du DataSeeder. */
@SpringBootTest
@Transactional
class AnnulationVenteACreditTests {

    private static final String ADMIN = "admin";

    @Autowired
    private GestionVentesService ventesService;
    @Autowired
    private GestionDetteClientService detteService;
    @Autowired
    private ClientCatalogueService clientService;
    @Autowired
    private SessionCaisseService sessionService;
    @Autowired
    private MouvementCaisseService mouvementCaisseService;
    @Autowired
    private JournalActiviteService journalService;
    @Autowired
    private ArticleRepository articleRepository;
    @Autowired
    private MouvementStockRepository mouvementRepository;
    @Autowired
    private VenteRepository venteRepository;
    @Autowired
    private EntityManager entityManager;

    private Article telephone() {
        return articleRepository.findByReferenceIgnoreCase("ELEC-001").orElseThrow();
    }

    private ClientResponse client() {
        return clientService.creer(new ClientRequest("Client annulation", null, "0100000000", "Adresse"));
    }

    private SessionCaisseResponse ouvrirSession() {
        return sessionService.ouvrir(new OuvertureSessionRequest(LocalDate.now(), new BigDecimal("1000")), ADMIN);
    }

    private VenteResponse venteACreditValidee(ClientResponse client, int quantite) {
        VenteResponse brouillon = ventesService.creerVenteDirecte(new VenteDirecteRequest(
            client.id(), true,
            List.of(new LigneDocumentRequest(telephone().getId(), null, BigDecimal.valueOf(quantite), null, null)),
            BigDecimal.ZERO, null, null), ADMIN);
        return ventesService.validerVente(brouillon.id(), ADMIN);
    }

    private Long sessionCaisseDuJour() {
        return sessionService.sessionOuverte(LocalDate.now()).id();
    }

    private void rafraichir() {
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void annulationRemetLeStockEnRayonEtAnnuleLaDette() {
        SessionCaisseResponse session = ouvrirSession();
        ClientResponse c = client();
        BigDecimal stockInitial = telephone().getQuantiteStock();
        VenteResponse vente = venteACreditValidee(c, 2);
        assertThat(telephone().getQuantiteStock()).isEqualByComparingTo(stockInitial.subtract(BigDecimal.valueOf(2)));
        rafraichir();

        VenteResponse annulee = ventesService.annulerVenteACredit(vente.id(), "Erreur de saisie", false, ADMIN).vente();
        rafraichir();

        assertThat(annulee.statut()).isEqualTo(StatutVente.ANNULEE);
        assertThat(telephone().getQuantiteStock()).isEqualByComparingTo(stockInitial);

        DetteResponse dette = detteService.listerDettesClient(c.id()).get(0);
        assertThat(dette.statut()).isEqualTo(StatutDette.ANNULEE);
        assertThat(dette.solde()).isEqualByComparingTo("0");
        assertThat(clientService.obtenirEncours(c.id()).montantDu()).isEqualByComparingTo("0");

        // la vente annulée ne compte plus dans le chiffre d'affaires de la session
        assertThat(venteRepository.chiffreAffairesSession(session.id(), StatutVente.VALIDEE)).isEqualByComparingTo("0");
        // le mouvement de stock de retour est tracé
        List<MouvementStock> mouvements = mouvementRepository.findByArticleIdAndDateMouvementBetweenOrderByDateMouvementDesc(
            telephone().getId(), LocalDateTime.now().minusMinutes(5), LocalDateTime.now().plusMinutes(5));
        assertThat(mouvements).anySatisfy(m -> {
            assertThat(m.getType()).isEqualTo(TypeMouvementStock.RETOUR_CLIENT);
            assertThat(m.getQuantite()).isEqualByComparingTo("2");
            assertThat(m.getStockApres()).isEqualByComparingTo(stockInitial);
        });
        assertThat(journalService.listerJournee(LocalDate.now()))
            .extracting(JournalActiviteResponse::action).contains("ANNULATION");
    }

    @Test
    void uneVenteAnnuleeNePeutPasEtreAnnuleeDeuxFoisEtLaDetteNeAcceptePlusDeReglement() {
        ouvrirSession();
        ClientResponse c = client();
        VenteResponse vente = venteACreditValidee(c, 1);
        rafraichir();
        ventesService.annulerVenteACredit(vente.id(), "Doublon", false, ADMIN);
        rafraichir();
        BigDecimal stockApres = telephone().getQuantiteStock();

        assertThatThrownBy(() -> ventesService.annulerVenteACredit(vente.id(), "Encore", false, ADMIN))
            .isInstanceOf(ConflitRessourceException.class);
        assertThat(telephone().getQuantiteStock()).isEqualByComparingTo(stockApres); // pas de double retour en stock

        DetteResponse dette = detteService.listerDettesClient(c.id()).get(0);
        assertThatThrownBy(() -> detteService.enregistrerReglement(dette.id(),
            new ReglementRequest(new BigDecimal("1"), ModeReglement.ESPECES, null), ADMIN))
            .isInstanceOf(OperationDetteInvalideException.class);
    }

    @Test
    void annulationRefuseeSiLeClientAdejaVerseUnReglement() {
        ouvrirSession();
        ClientResponse c = client();
        BigDecimal stockInitial = telephone().getQuantiteStock();
        VenteResponse vente = venteACreditValidee(c, 1);
        rafraichir();
        DetteResponse dette = detteService.listerDettesClient(c.id()).get(0);
        detteService.enregistrerReglement(dette.id(),
            new ReglementRequest(new BigDecimal("1000"), ModeReglement.ESPECES, null), ADMIN);
        rafraichir();

        // sans la confirmation que le client rapporte les articles en l'état, rien ne change
        assertThatThrownBy(() -> ventesService.annulerVenteACredit(vente.id(), "Client mécontent", false, ADMIN))
            .isInstanceOf(ConflitRessourceException.class)
            .hasMessageContaining("articles en l'état");
        assertThat(telephone().getQuantiteStock()).isEqualByComparingTo(stockInitial.subtract(BigDecimal.valueOf(1)));
        assertThat(detteService.obtenirDette(dette.id()).statut()).isEqualTo(StatutDette.PARTIELLEMENT_REGLEE);
        assertThat(mouvementCaisseService.lister(sessionCaisseDuJour()).stream()
            .filter(m -> m.motif().startsWith("Remboursement"))).isEmpty();
    }

    @Test
    void ventePartiellementPayeeEnEspecesEstAnnuleeEtRembourseeEnEspeces() {
        SessionCaisseResponse session = ouvrirSession();
        ClientResponse c = client();
        BigDecimal stockInitial = telephone().getQuantiteStock();
        VenteResponse vente = venteACreditValidee(c, 1);
        rafraichir();
        DetteResponse dette = detteService.listerDettesClient(c.id()).get(0);
        detteService.enregistrerReglement(dette.id(),
            new ReglementRequest(new BigDecimal("100000"), ModeReglement.ESPECES, null), ADMIN);
        rafraichir();

        AnnulationVenteResponse resultat = ventesService.annulerVenteACredit(
            vente.id(), "Client mécontent", true, ADMIN);
        rafraichir();

        assertThat(resultat.vente().statut()).isEqualTo(StatutVente.ANNULEE);
        assertThat(resultat.montantRembourse()).isEqualByComparingTo("100000");
        assertThat(resultat.remboursementEspeces()).isEqualByComparingTo("100000");
        assertThat(resultat.remboursementAutresModes()).isEqualByComparingTo("0");
        assertThat(telephone().getQuantiteStock()).isEqualByComparingTo(stockInitial);
        assertThat(detteService.obtenirDette(dette.id()).statut()).isEqualTo(StatutDette.ANNULEE);
        assertThat(clientService.obtenirEncours(c.id()).montantDu()).isEqualByComparingTo("0");

        // les 100 000 rendus au client sortent de la caisse : le solde théorique revient au fond de caisse
        assertThat(mouvementCaisseService.lister(session.id())).anySatisfy(m -> {
            assertThat(m.montant()).isEqualByComparingTo("100000");
            assertThat(m.motif()).contains("Remboursement").contains(vente.code());
        });
        assertThat(sessionService.obtenir(session.id()).soldeTheorique()).isEqualByComparingTo("1000");
    }

    @Test
    void venteReglementParCartePuisAnnuleeNeSortAucuneEspece() {
        ouvrirSession();
        ClientResponse c = client();
        VenteResponse vente = venteACreditValidee(c, 1);
        rafraichir();
        DetteResponse dette = detteService.listerDettesClient(c.id()).get(0);
        detteService.enregistrerReglement(dette.id(),
            new ReglementRequest(new BigDecimal("245000"), ModeReglement.CARTE, "TPE-9"), ADMIN);
        rafraichir();

        AnnulationVenteResponse resultat = ventesService.annulerVenteACredit(vente.id(), "Retour", true, ADMIN);

        assertThat(resultat.montantRembourse()).isEqualByComparingTo("245000");
        assertThat(resultat.remboursementEspeces()).isEqualByComparingTo("0");
        assertThat(resultat.remboursementAutresModes()).isEqualByComparingTo("245000");
        assertThat(mouvementCaisseService.lister(sessionCaisseDuJour())).isEmpty();
        assertThat(journalService.listerJournee(LocalDate.now()))
            .filteredOn(a -> "ANNULATION".equals(a.action()))
            .anySatisfy(a -> assertThat(a.details()).contains("autres modes 245000"));
    }

    @Test
    void remboursementEnEspecesRefuseSiLaCaisseNeContientPasAssezEtRienNeChange() {
        SessionCaisseResponse session = ouvrirSession(); // fond de caisse : 1 000
        ClientResponse c = client();
        BigDecimal stockInitial = telephone().getQuantiteStock();
        VenteResponse vente = venteACreditValidee(c, 1);
        rafraichir();
        DetteResponse dette = detteService.listerDettesClient(c.id()).get(0);
        detteService.enregistrerReglement(dette.id(),
            new ReglementRequest(new BigDecimal("100000"), ModeReglement.ESPECES, null), ADMIN);
        // le gérant a vidé la caisse entre-temps : il ne reste que 500 en espèces
        mouvementCaisseService.enregistrer(
            new MouvementCaisseRequest("Dépôt en banque", "Banque", new BigDecimal("100500")), ADMIN);
        rafraichir();

        assertThatThrownBy(() -> ventesService.annulerVenteACredit(vente.id(), "Retour", true, ADMIN))
            .isInstanceOf(ConflitRessourceException.class)
            .hasMessageContaining("solde théorique");
        assertThat(telephone().getQuantiteStock()).isEqualByComparingTo(stockInitial.subtract(BigDecimal.valueOf(1)));
        assertThat(detteService.obtenirDette(dette.id()).statut()).isEqualTo(StatutDette.PARTIELLEMENT_REGLEE);
        assertThat(mouvementCaisseService.lister(session.id())).hasSize(1); // seul le dépôt existe
    }

    @Test
    void ardoiseSoldeeSeClotureSeuleEtResteSoldee() {
        ouvrirSession();
        ClientResponse c = client();
        BigDecimal stockInitial = telephone().getQuantiteStock();
        VenteResponse vente = venteACreditValidee(c, 1);
        rafraichir();
        DetteResponse dette = detteService.listerDettesClient(c.id()).get(0);

        // deux versements qui, ensemble, soldent l'ardoise (245 000)
        detteService.enregistrerReglement(dette.id(),
            new ReglementRequest(new BigDecimal("100000"), ModeReglement.ESPECES, null), ADMIN);
        assertThat(detteService.obtenirDette(dette.id()).statut()).isEqualTo(StatutDette.PARTIELLEMENT_REGLEE);
        detteService.enregistrerReglement(dette.id(),
            new ReglementRequest(new BigDecimal("145000"), ModeReglement.ESPECES, null), ADMIN);
        rafraichir();

        // la dette passe seule à « réglée », sans action supplémentaire
        DetteResponse soldee = detteService.obtenirDette(dette.id());
        assertThat(soldee.statut()).isEqualTo(StatutDette.REGLEE);
        assertThat(soldee.solde()).isEqualByComparingTo("0");
        assertThat(clientService.obtenirEncours(c.id()).montantDu()).isEqualByComparingTo("0");

        // une vente soldée ne s'annule pas sans que le client rapporte les articles en l'état
        assertThatThrownBy(() -> ventesService.annulerVenteACredit(vente.id(), "Retour", false, ADMIN))
            .isInstanceOf(ConflitRessourceException.class);
        assertThat(detteService.obtenirDette(dette.id()).statut()).isEqualTo(StatutDette.REGLEE);
        assertThat(telephone().getQuantiteStock()).isEqualByComparingTo(stockInitial.subtract(BigDecimal.valueOf(1)));

        // avec les articles rapportés en l'état, elle s'annule et les 245 000 versés sont remboursés
        AnnulationVenteResponse resultat = ventesService.annulerVenteACredit(vente.id(), "Retour", true, ADMIN);
        rafraichir();
        assertThat(resultat.montantRembourse()).isEqualByComparingTo("245000");
        assertThat(resultat.remboursementEspeces()).isEqualByComparingTo("245000");
        assertThat(telephone().getQuantiteStock()).isEqualByComparingTo(stockInitial);
        assertThat(detteService.obtenirDette(dette.id()).statut()).isEqualTo(StatutDette.ANNULEE);
    }

    @Test
    void seuleUneVenteACreditValideePeutEtreAnnulee() {
        ouvrirSession();
        BigDecimal stockInitial = telephone().getQuantiteStock();

        // vente au comptant validée
        VenteResponse comptant = ventesService.validerVente(ventesService.creerVenteDirecte(new VenteDirecteRequest(
            null, false,
            List.of(new LigneDocumentRequest(telephone().getId(), null, BigDecimal.valueOf(1), null, null)),
            BigDecimal.ZERO, ModeReglement.ESPECES, new BigDecimal("1000000")), ADMIN).id(), ADMIN);
        assertThatThrownBy(() -> ventesService.annulerVenteACredit(comptant.id(), "Test", false, ADMIN))
            .isInstanceOf(ConflitRessourceException.class)
            .hasMessageContaining("crédit");

        // brouillon à crédit non validé
        VenteResponse brouillon = ventesService.creerVenteDirecte(new VenteDirecteRequest(
            client().id(), true,
            List.of(new LigneDocumentRequest(telephone().getId(), null, BigDecimal.valueOf(1), null, null)),
            BigDecimal.ZERO, null, null), ADMIN);
        assertThatThrownBy(() -> ventesService.annulerVenteACredit(brouillon.id(), "Test", false, ADMIN))
            .isInstanceOf(ConflitRessourceException.class)
            .hasMessageContaining("validée");

        assertThat(telephone().getQuantiteStock()).isEqualByComparingTo(stockInitial.subtract(BigDecimal.valueOf(1)));
    }
}
