package com.gesstock.gesstock.stock;

import com.gesstock.gesstock.approvisionnements.dto.ApprovisionnementRequest;
import com.gesstock.gesstock.approvisionnements.dto.ApprovisionnementResponse;
import com.gesstock.gesstock.approvisionnements.dto.FournisseurRequest;
import com.gesstock.gesstock.approvisionnements.dto.FournisseurResponse;
import com.gesstock.gesstock.approvisionnements.dto.LigneApprovisionnementRequest;
import com.gesstock.gesstock.approvisionnements.service.EntreeStockService;
import com.gesstock.gesstock.approvisionnements.service.FournisseurCatalogueService;
import com.gesstock.gesstock.articles.dto.ArticleRequest;
import com.gesstock.gesstock.articles.service.ArticleCatalogueService;
import com.gesstock.gesstock.entities.Article;
import com.gesstock.gesstock.entities.ModeReglement;
import com.gesstock.gesstock.entities.StatutVente;
import com.gesstock.gesstock.referentiels.dto.OuvertureSessionRequest;
import com.gesstock.gesstock.referentiels.service.SessionCaisseService;
import com.gesstock.gesstock.repositories.ArticleRepository;
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
 * Le prix d'achat tapé dans la fiche d'un article vaut pour le stock en rayon (c'est le coût de l'article, donc de ses ventes) ;
 * les bons d'approvisionnement le font ensuite évoluer par pondération. Avant : la fiche changeait le « dernier prix d'achat »
 * mais pas le coût, et le bénéfice par article l'ignorait (articles chargés à 0 puis tarifés à la main : bénéfice = chiffre d'affaires).
 */
@SpringBootTest
@Transactional
class CoutDeLArticleModifieDansLaFicheTests {

    private static final String ADMIN = "admin";

    @Autowired
    private ArticleCatalogueService articleService;
    @Autowired
    private EntreeStockService entreeService;
    @Autowired
    private FournisseurCatalogueService fournisseurService;
    @Autowired
    private GestionVentesService ventesService;
    @Autowired
    private SessionCaisseService sessionService;
    @Autowired
    private ArticleRepository articleRepository;
    @Autowired
    private VenteRepository venteRepository;

    private Article telephone() {
        return articleRepository.findByReferenceIgnoreCase("ELEC-001").orElseThrow(); // stock 12, prix d'achat 189 000, prix de vente 245 000
    }

    private void modifierLaFiche(String prixAchat) {
        Article article = telephone();
        articleService.modifier(article.getId(), new ArticleRequest(article.getReference(), article.getDesignation(),
            new BigDecimal(prixAchat), article.getPrix(), article.getSeuilAlerte(), article.getQuantiteStock(), null, null));
    }

    private void recevoir(String numero, int quantite, String prix) {
        FournisseurResponse fournisseur = fournisseurService.creer(
            new FournisseurRequest("Fournisseur " + numero, "0100000000", "f@example.com", "Adresse"));
        ApprovisionnementResponse bon = entreeService.creer(new ApprovisionnementRequest(numero, LocalDate.now(), fournisseur.id(),
            List.of(new LigneApprovisionnementRequest(telephone().getId(), BigDecimal.valueOf(quantite), new BigDecimal(prix)))));
        entreeService.valider(bon.id(), ADMIN);
    }

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
    void lePrixDAchatDeLaFicheDevientLeCoutEtLaMargeLeSuit() {
        modifierLaFiche("200000");

        Article article = telephone();
        assertThat(article.getPrixAchat()).isEqualByComparingTo("200000");
        assertThat(article.getCmp()).as("le coût du stock en rayon suit le prix saisi").isEqualByComparingTo("200000");

        sessionService.ouvrir(new OuvertureSessionRequest(null, new BigDecimal("1000")), ADMIN);
        VenteResponse brouillon = ventesService.creerVenteDirecte(new VenteDirecteRequest(null, false,
            List.of(new LigneDocumentRequest(article.getId(), null, BigDecimal.ONE, null, null)),
            BigDecimal.ZERO, ModeReglement.ESPECES, new BigDecimal("10000000")), ADMIN);
        ventesService.validerVente(brouillon.id(), ADMIN);
        assertThat(margeDuTelephone()).as("245 000 - 200 000, pas 245 000 - l'ancien coût").isEqualByComparingTo("45000");
    }

    @Test
    void unBonRecuApresLaFichePondereLeStockEnRayonAuPrixDeLaFicheEtLeNouveauStockAuPrixDuBon() {
        modifierLaFiche("200000");

        recevoir("BR-FICHE-1", 10, "230000");

        assertThat(telephone().getCmp()).as("(12 × 200 000 + 10 × 230 000) / 22").isEqualByComparingTo("213636.3636");
        assertThat(telephone().getPrixAchat()).as("dernier prix : celui du bon").isEqualByComparingTo("230000");
    }

    @Test
    void modifierAutreChoseQueLePrixDAchatLaisseLeCoutPondereDeLArticleIntact() {
        recevoir("BR-FICHE-2", 10, "230000");
        BigDecimal coutPondere = telephone().getCmp();
        assertThat(coutPondere).isEqualByComparingTo("207636.3636");

        // l'écran renvoie le dernier prix d'achat tel qu'il l'a lu : seule la désignation change
        Article article = telephone();
        articleService.modifier(article.getId(), new ArticleRequest(article.getReference(), "Téléphone (nouveau nom)",
            article.getPrixAchat(), article.getPrix(), article.getSeuilAlerte(), article.getQuantiteStock(), null, null));

        assertThat(telephone().getCmp()).isEqualByComparingTo(coutPondere);
    }

    @Test
    void unCoutResteAZeroAlorsQuUnPrixDAchatExisteEstRattrapeALaPremiereModification() {
        Article article = telephone();
        article.setCmp(BigDecimal.ZERO);   // article chargé à 0, tarifé ensuite à la main
        articleRepository.saveAndFlush(article);

        Article avant = telephone();
        articleService.modifier(avant.getId(), new ArticleRequest(avant.getReference(), avant.getDesignation(),
            avant.getPrixAchat(), new BigDecimal("250000"), avant.getSeuilAlerte(), avant.getQuantiteStock(), null, null));

        assertThat(telephone().getCmp()).isEqualByComparingTo("189000");
    }
}
