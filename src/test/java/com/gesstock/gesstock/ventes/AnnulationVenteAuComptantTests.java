package com.gesstock.gesstock.ventes;

import com.gesstock.gesstock.caisse.dto.MouvementCaisseRequest;
import com.gesstock.gesstock.caisse.service.MouvementCaisseService;
import com.gesstock.gesstock.clients.dto.ClientRequest;
import com.gesstock.gesstock.clients.dto.ClientResponse;
import com.gesstock.gesstock.clients.service.ClientCatalogueService;
import com.gesstock.gesstock.common.exception.ConflitRessourceException;
import com.gesstock.gesstock.entities.Article;
import com.gesstock.gesstock.entities.JournalActivite;
import com.gesstock.gesstock.entities.ModeReglement;
import com.gesstock.gesstock.entities.MouvementStock;
import com.gesstock.gesstock.entities.StatutVente;
import com.gesstock.gesstock.entities.TypeMouvementStock;
import com.gesstock.gesstock.referentiels.dto.ClotureSessionRequest;
import com.gesstock.gesstock.referentiels.dto.OuvertureSessionRequest;
import com.gesstock.gesstock.referentiels.dto.SessionCaisseResponse;
import com.gesstock.gesstock.referentiels.service.SessionCaisseService;
import com.gesstock.gesstock.repositories.ArticleRepository;
import com.gesstock.gesstock.repositories.JournalActiviteRepository;
import com.gesstock.gesstock.repositories.MouvementStockRepository;
import com.gesstock.gesstock.repositories.VenteRepository;
import com.gesstock.gesstock.ventes.dto.AnnulationVenteResponse;
import com.gesstock.gesstock.ventes.dto.LigneDocumentRequest;
import com.gesstock.gesstock.ventes.dto.VenteDirecteRequest;
import com.gesstock.gesstock.ventes.dto.VenteResponse;
import com.gesstock.gesstock.ventes.service.GestionVentesService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Un client qui rapporte ses articles en l'état est remboursé, le jour même de la vente et tant que la journée de caisse est
 * ouverte. Article : ELEC-001 à 245 000 (stock 12, coût moyen 189 000).
 */
@SpringBootTest
@Transactional
class AnnulationVenteAuComptantTests {

    private static final String ADMIN = "admin";

    @Autowired
    private GestionVentesService ventesService;
    @Autowired
    private SessionCaisseService sessionService;
    @Autowired
    private MouvementCaisseService mouvementCaisseService;
    @Autowired
    private ClientCatalogueService clientService;
    @Autowired
    private ArticleRepository articleRepository;
    @Autowired
    private VenteRepository venteRepository;
    @Autowired
    private MouvementStockRepository mouvementRepository;
    @Autowired
    private JournalActiviteRepository journalRepository;

    private Article telephone() {
        return articleRepository.findByReferenceIgnoreCase("ELEC-001").orElseThrow();
    }

    private SessionCaisseResponse ouvrirSession() {
        return sessionService.ouvrir(new OuvertureSessionRequest(null, new BigDecimal("1000")), ADMIN);
    }

    private VenteResponse vendre(ModeReglement mode) {
        BigDecimal recu = mode == ModeReglement.ESPECES ? new BigDecimal("250000") : new BigDecimal("245000");
        VenteResponse brouillon = ventesService.creerVenteDirecte(new VenteDirecteRequest(null, false,
            List.of(new LigneDocumentRequest(telephone().getId(), null, 1, null, null)), BigDecimal.ZERO, mode, recu), ADMIN);
        return ventesService.validerVente(brouillon.id(), ADMIN);
    }

    private BigDecimal soldeTheorique(SessionCaisseResponse session) {
        return sessionService.obtenir(session.id()).soldeTheorique();
    }

    @Test
    void uneVenteEnEspecesAnnuleeRemetLeStockEtRendLArgentSansDoubleDeduction() {
        SessionCaisseResponse session = ouvrirSession();
        int stockAvantVente = telephone().getQuantiteStock();
        VenteResponse vente = vendre(ModeReglement.ESPECES);
        assertThat(soldeTheorique(session)).isEqualByComparingTo("246000"); // 1 000 de fond + 245 000

        AnnulationVenteResponse reponse = ventesService.annulerVente(vente.id(), "Client mécontent", true, ADMIN);

        assertThat(reponse.vente().statut()).isEqualTo(StatutVente.ANNULEE);
        assertThat(reponse.montantRembourse()).isEqualByComparingTo("245000");
        assertThat(reponse.remboursementEspeces()).isEqualByComparingTo("245000");
        assertThat(reponse.remboursementAutresModes()).isZero();
        assertThat(telephone().getQuantiteStock()).as("stock remis en rayon").isEqualTo(stockAvantVente);
        assertThat(soldeTheorique(session)).as("la caisse est revenue au fond, le remboursement n'est déduit qu'une fois")
            .isEqualByComparingTo("1000");
        assertThat(venteRepository.chiffreAffairesSession(session.id(), StatutVente.VALIDEE)).isZero();

        List<MouvementStock> retours = mouvementRepository.findByArticleIdAndDateMouvementBetweenOrderByDateMouvementDesc(
                telephone().getId(), LocalDate.now().atStartOfDay(), LocalDate.now().plusDays(1).atStartOfDay()).stream()
            .filter(mouvement -> mouvement.getType() == TypeMouvementStock.RETOUR_CLIENT).toList();
        assertThat(retours).singleElement().satisfies(mouvement -> {
            assertThat(mouvement.getCoutUnitaire()).as("rentre au coût de la vente").isEqualByComparingTo("189000");
            assertThat(mouvement.getCommentaire()).contains("Client mécontent");
        });
        assertThat(journalRepository.findAll()).filteredOn(entree -> "ANNULATION".equals(entree.getAction()))
            .singleElement().extracting(JournalActivite::getDetails).asString()
            .contains("vente au comptant").contains(vente.code()).contains("245000").contains("espèces");
    }

    @Test
    void sansArticlesRapportesEnEtatRienNeChange() {
        SessionCaisseResponse session = ouvrirSession();
        VenteResponse vente = vendre(ModeReglement.ESPECES);
        int stockApresVente = telephone().getQuantiteStock();

        assertThatThrownBy(() -> ventesService.annulerVente(vente.id(), "Test", false, ADMIN))
            .isInstanceOf(ConflitRessourceException.class)
            .hasMessageContaining("rapporte les articles en l'état");

        assertThat(venteRepository.findById(vente.id()).orElseThrow().getStatut()).isEqualTo(StatutVente.VALIDEE);
        assertThat(telephone().getQuantiteStock()).isEqualTo(stockApresVente);
        assertThat(soldeTheorique(session)).isEqualByComparingTo("246000");
    }

    @Test
    void apresLaClotureOnNeRouvrePasLaJourneeDeCaisse() {
        SessionCaisseResponse session = ouvrirSession();
        VenteResponse vente = vendre(ModeReglement.ESPECES);
        sessionService.cloturer(session.id(), new ClotureSessionRequest(new BigDecimal("246000")), ADMIN);

        assertThatThrownBy(() -> ventesService.annulerVente(vente.id(), "Trop tard", true, ADMIN))
            .isInstanceOf(ConflitRessourceException.class)
            .hasMessageContaining("clôturée");
        assertThat(venteRepository.findById(vente.id()).orElseThrow().getStatut()).isEqualTo(StatutVente.VALIDEE);
    }

    @Test
    void uneCaisseSansAssezDEspecesRefuseLeRemboursement() {
        SessionCaisseResponse session = ouvrirSession();
        VenteResponse vente = vendre(ModeReglement.ESPECES);
        // le cash a été sorti entre-temps (paiement d'un fournisseur) : il ne reste rien pour rembourser
        mouvementCaisseService.enregistrer(new MouvementCaisseRequest("Paiement fournisseur", "Fournisseur X", new BigDecimal("246000")), ADMIN);
        assertThat(soldeTheorique(session)).isZero();

        assertThatThrownBy(() -> ventesService.annulerVente(vente.id(), "Retour", true, ADMIN))
            .isInstanceOf(ConflitRessourceException.class)
            .hasMessageContaining("pas assez d'espèces");
        assertThat(venteRepository.findById(vente.id()).orElseThrow().getStatut()).isEqualTo(StatutVente.VALIDEE);
    }

    @Test
    void uneVentePayeeParCarteSeRembourseParLeMemeMoyenSansToucherLaCaisse() {
        SessionCaisseResponse session = ouvrirSession();
        VenteResponse vente = vendre(ModeReglement.CARTE);
        assertThat(soldeTheorique(session)).as("un paiement par carte n'entre pas dans le tiroir").isEqualByComparingTo("1000");

        AnnulationVenteResponse reponse = ventesService.annulerVente(vente.id(), "Retour", true, ADMIN);

        assertThat(reponse.remboursementEspeces()).isZero();
        assertThat(reponse.remboursementAutresModes()).isEqualByComparingTo("245000");
        assertThat(soldeTheorique(session)).isEqualByComparingTo("1000");
    }

    @Test
    void uneVenteDejaAnnuleeNePeutPasLEtreDeuxFois() {
        ouvrirSession();
        VenteResponse vente = vendre(ModeReglement.ESPECES);
        ventesService.annulerVente(vente.id(), "Retour", true, ADMIN);

        assertThatThrownBy(() -> ventesService.annulerVente(vente.id(), "Encore", true, ADMIN))
            .isInstanceOf(ConflitRessourceException.class)
            .hasMessageContaining("Seule une vente validée");
    }

    @Test
    void uneVenteACreditPasseToujoursParLeCircuitDeLaDette() {
        ouvrirSession();
        ClientResponse client = clientService.creer(new ClientRequest("Client crédit", null, null, null));
        VenteResponse brouillon = ventesService.creerVenteDirecte(new VenteDirecteRequest(client.id(), true,
            List.of(new LigneDocumentRequest(telephone().getId(), null, 1, null, null)), BigDecimal.ZERO, null, null), ADMIN);
        VenteResponse vente = ventesService.validerVente(brouillon.id(), ADMIN);

        AnnulationVenteResponse reponse = ventesService.annulerVente(vente.id(), "Erreur", false, ADMIN);

        assertThat(reponse.vente().statut()).isEqualTo(StatutVente.ANNULEE);
        assertThat(reponse.montantRembourse()).as("aucun versement à rembourser").isZero();
    }
}
