package com.gesstock.gesstock.stock;

import com.gesstock.gesstock.approvisionnements.dto.ApprovisionnementRequest;
import com.gesstock.gesstock.approvisionnements.dto.ApprovisionnementResponse;
import com.gesstock.gesstock.approvisionnements.dto.FournisseurRequest;
import com.gesstock.gesstock.approvisionnements.dto.FournisseurResponse;
import com.gesstock.gesstock.approvisionnements.dto.LigneApprovisionnementRequest;
import com.gesstock.gesstock.approvisionnements.service.EntreeStockService;
import com.gesstock.gesstock.approvisionnements.service.FournisseurCatalogueService;
import com.gesstock.gesstock.clients.dto.ClientRequest;
import com.gesstock.gesstock.clients.dto.ClientResponse;
import com.gesstock.gesstock.clients.service.ClientCatalogueService;
import com.gesstock.gesstock.entities.Article;
import com.gesstock.gesstock.entities.HistoriquePrixAchat;
import com.gesstock.gesstock.entities.ModeReglement;
import com.gesstock.gesstock.entities.MouvementStock;
import com.gesstock.gesstock.entities.StatutVente;
import com.gesstock.gesstock.entities.TypeMouvementStock;
import com.gesstock.gesstock.referentiels.dto.OuvertureSessionRequest;
import com.gesstock.gesstock.referentiels.service.SessionCaisseService;
import com.gesstock.gesstock.repositories.ArticleRepository;
import com.gesstock.gesstock.repositories.HistoriquePrixAchatRepository;
import com.gesstock.gesstock.repositories.MouvementStockRepository;
import com.gesstock.gesstock.repositories.VenteRepository;
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
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le coût d'un article est son coût moyen pondéré (CMP), mémorisé sur chaque vente : une réception postérieure ne réécrit
 * pas la marge des ventes passées (mesuré avant correction : 56 000 devenus 15 000).
 */
@SpringBootTest
@Transactional
class CoutMoyenPondereTests {

    private static final String ADMIN = "admin";

    @Autowired
    private EntreeStockService entreeService;
    @Autowired
    private FournisseurCatalogueService fournisseurService;
    @Autowired
    private GestionVentesService ventesService;
    @Autowired
    private SessionCaisseService sessionService;
    @Autowired
    private ClientCatalogueService clientService;
    @Autowired
    private ArticleRepository articleRepository;
    @Autowired
    private VenteRepository venteRepository;
    @Autowired
    private HistoriquePrixAchatRepository historiqueRepository;
    @Autowired
    private MouvementStockRepository mouvementRepository;

    private Article telephone() {
        return articleRepository.findByReferenceIgnoreCase("ELEC-001").orElseThrow(); // stock 12, prix d'achat 189 000
    }

    private void recevoir(String numero, int quantite, String prix) {
        FournisseurResponse fournisseur = fournisseurService.creer(
            new FournisseurRequest("Fournisseur " + numero, "0100000000", "f@example.com", "Adresse"));
        ApprovisionnementResponse bon = entreeService.creer(new ApprovisionnementRequest(numero, LocalDate.now(), fournisseur.id(),
            List.of(new LigneApprovisionnementRequest(telephone().getId(), quantite, new BigDecimal(prix)))));
        entreeService.valider(bon.id(), ADMIN);
    }

    private VenteResponse vendre(int quantite, Long clientId, boolean aCredit) {
        VenteResponse brouillon = ventesService.creerVenteDirecte(new VenteDirecteRequest(clientId, aCredit,
            List.of(new LigneDocumentRequest(telephone().getId(), null, quantite, null, null)),
            BigDecimal.ZERO, aCredit ? null : ModeReglement.ESPECES, aCredit ? null : new BigDecimal("10000000")), ADMIN);
        return ventesService.validerVente(brouillon.id(), ADMIN);
    }

    /** Marge brute de ELEC-001 sur la journée, telle que la calculent les rapports (chiffre d'affaires - coût mémorisé). */
    private BigDecimal margeDuTelephone() {
        LocalDateTime debut = LocalDate.now().atStartOfDay();
        for (Object[] ligne : venteRepository.margeParArticle(StatutVente.VALIDEE, debut, debut.plusDays(1))) {
            if (ligne[1].equals("ELEC-001")) {
                return ((BigDecimal) ligne[4]).subtract((BigDecimal) ligne[5]);
            }
        }
        throw new AssertionError("ELEC-001 absent du rapport de marge");
    }

    @Test
    void uneReceptionPondereLeCoutEtLaisseUneLigneDHistorique() {
        recevoir("BR-CMP-1", 10, "230000");

        Article article = telephone();
        assertThat(article.getQuantiteStock()).isEqualTo(22);
        assertThat(article.getCmp()).as("(12 × 189 000 + 10 × 230 000) / 22").isEqualByComparingTo("207636.3636");
        assertThat(article.getPrixAchat()).as("dernier prix d'achat, à titre informatif").isEqualByComparingTo("230000");

        List<HistoriquePrixAchat> historique = historiqueRepository.findByArticleIdOrderByDateEffetDescIdDesc(article.getId());
        assertThat(historique).hasSize(1);
        HistoriquePrixAchat ligne = historique.get(0);
        assertThat(ligne.getAncienPrix()).isEqualByComparingTo("189000");
        assertThat(ligne.getNouveauPrix()).isEqualByComparingTo("230000");
        assertThat(ligne.getAncienCmp()).isEqualByComparingTo("189000");
        assertThat(ligne.getNouveauCmp()).isEqualByComparingTo("207636.3636");
        assertThat(ligne.getStockAvant()).isEqualTo(12);
        assertThat(ligne.getQuantiteRecue()).isEqualTo(10);
        assertThat(ligne.getUtilisateur().getUsername()).isEqualTo(ADMIN);
    }

    @Test
    void laMargeDUneVentePasseeNeBougePasQuandOnReceptionneUnLotPlusCher() {
        sessionService.ouvrir(new OuvertureSessionRequest(null, new BigDecimal("1000")), ADMIN);
        VenteResponse vente = vendre(1, null, false);
        assertThat(vente.statut()).isEqualTo(StatutVente.VALIDEE);
        assertThat(margeDuTelephone()).as("245 000 - 189 000").isEqualByComparingTo("56000");

        recevoir("BR-CMP-2", 10, "230000");

        assertThat(margeDuTelephone()).as("la marge de la vente déjà faite est inchangée").isEqualByComparingTo("56000");
        assertThat(telephone().getCmp()).isGreaterThan(new BigDecimal("189000"));
    }

    @Test
    void lesSortiesMemorisentLeCoutEtUneAnnulationRemetLeStockAuCoutDeLaVente() {
        sessionService.ouvrir(new OuvertureSessionRequest(null, new BigDecimal("1000")), ADMIN);
        ClientResponse client = clientService.creer(new ClientRequest("Client coût", null, null, null));
        VenteResponse vente = vendre(2, client.id(), true);           // stock 12 -> 10, sortie au CMP du moment (189 000)
        recevoir("BR-CMP-3", 10, "230000");                           // stock 20, CMP (10 × 189 000 + 10 × 230 000) / 20
        assertThat(telephone().getCmp()).isEqualByComparingTo("209500");

        ventesService.annulerVenteACredit(vente.id(), "Retour client", false, ADMIN);

        Article article = telephone();
        assertThat(article.getQuantiteStock()).isEqualTo(22);
        // les 2 unités reviennent à 189 000 (leur coût à la vente), pas au CMP du jour : (20 × 209 500 + 2 × 189 000) / 22
        assertThat(article.getCmp()).isEqualByComparingTo("207636.3636");

        List<MouvementStock> mouvements = mouvementRepository.findByArticleIdAndDateMouvementBetweenOrderByDateMouvementDesc(
            article.getId(), LocalDate.now().atStartOfDay(), LocalDate.now().plusDays(1).atStartOfDay());
        assertThat(mouvements).filteredOn(m -> m.getType() == TypeMouvementStock.SORTIE_VENTE)
            .singleElement().satisfies(m -> assertThat(m.getCoutUnitaire()).isEqualByComparingTo("189000"));
        assertThat(mouvements).filteredOn(m -> m.getType() == TypeMouvementStock.RETOUR_CLIENT)
            .singleElement().satisfies(m -> assertThat(m.getCoutUnitaire()).isEqualByComparingTo("189000"));
        assertThat(mouvements).filteredOn(m -> m.getType() == TypeMouvementStock.ENTREE_APPROVISIONNEMENT)
            .singleElement().satisfies(m -> assertThat(m.getCoutUnitaire()).isEqualByComparingTo("230000"));
    }

    @Test
    void laValeurDuStockEstLaSommeDesQuantitesParLeCoutMoyen() {
        recevoir("BR-CMP-4", 10, "230000");

        BigDecimal attendu = articleRepository.findAll().stream()
            .map(article -> article.getCmp().multiply(BigDecimal.valueOf(article.getQuantiteStock())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(articleRepository.valeurTotaleStock()).isEqualByComparingTo(attendu);
        assertThat(articleRepository.valeurTotaleStock()).as("l'ancien calcul (dernier prix × quantité) donnait davantage")
            .isLessThan(articleRepository.findAll().stream()
                .map(article -> article.getPrixAchat().multiply(BigDecimal.valueOf(article.getQuantiteStock())))
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }
}
