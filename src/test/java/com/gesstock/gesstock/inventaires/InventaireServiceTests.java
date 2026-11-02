package com.gesstock.gesstock.inventaires;

import com.gesstock.gesstock.common.exception.ConflitRessourceException;
import com.gesstock.gesstock.common.exception.RequeteInvalideException;
import com.gesstock.gesstock.entities.Article;
import com.gesstock.gesstock.entities.CategorieArticle;
import com.gesstock.gesstock.entities.JournalActivite;
import com.gesstock.gesstock.entities.ModeReglement;
import com.gesstock.gesstock.entities.MotifAjustementStock;
import com.gesstock.gesstock.entities.MouvementStock;
import com.gesstock.gesstock.entities.PerimetreInventaire;
import com.gesstock.gesstock.entities.StatutInventaire;
import com.gesstock.gesstock.entities.TypeMouvementStock;
import com.gesstock.gesstock.inventaires.dto.InventaireRequest;
import com.gesstock.gesstock.inventaires.dto.InventaireResponse;
import com.gesstock.gesstock.inventaires.dto.LigneInventaireRequest;
import com.gesstock.gesstock.inventaires.dto.LigneInventaireResponse;
import com.gesstock.gesstock.inventaires.service.InventaireService;
import com.gesstock.gesstock.referentiels.dto.OuvertureSessionRequest;
import com.gesstock.gesstock.referentiels.service.SessionCaisseService;
import com.gesstock.gesstock.repositories.ArticleRepository;
import com.gesstock.gesstock.repositories.CategorieArticleRepository;
import com.gesstock.gesstock.repositories.JournalActiviteRepository;
import com.gesstock.gesstock.repositories.MouvementStockRepository;
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
 * Inventaire physique. Données de démonstration : ELEC-001 (stock 12, coût moyen 189 000), ALIM-001 (180, 900),
 * ALIM-002 (75), ELEC-002 (5), MOB-001 (18), MOB-002 (7). Ouvert par « gestionnaire », validé par « admin ».
 */
@SpringBootTest
@Transactional
class InventaireServiceTests {

    private static final String OUVREUR = "gestionnaire";
    private static final String VALIDATEUR = "admin";

    @Autowired
    private InventaireService inventaireService;
    @Autowired
    private ArticleRepository articleRepository;
    @Autowired
    private CategorieArticleRepository categorieRepository;
    @Autowired
    private MouvementStockRepository mouvementRepository;
    @Autowired
    private JournalActiviteRepository journalRepository;
    @Autowired
    private GestionVentesService ventesService;
    @Autowired
    private SessionCaisseService sessionService;

    private Article article(String reference) {
        return articleRepository.findByReferenceIgnoreCase(reference).orElseThrow();
    }

    private InventaireResponse ouvrirTout() {
        return inventaireService.ouvrir(new InventaireRequest(PerimetreInventaire.TOUT, null, null), OUVREUR);
    }

    private void compter(InventaireResponse inventaire, String reference, int compte, MotifAjustementStock motif, String commentaire) {
        inventaireService.saisir(inventaire.id(), article(reference).getId(),
            new LigneInventaireRequest(BigDecimal.valueOf(compte), motif, commentaire), OUVREUR);
    }

    @Test
    void unInventaireSuitSonCycleEtAppliqueLEcartAuStockAvecSaTrace() {
        InventaireResponse inventaire = ouvrirTout();
        assertThat(inventaire.statut()).isEqualTo(StatutInventaire.EN_SAISIE);
        assertThat(inventaire.nombreLignes()).isEqualTo(6);
        assertThat(inventaire.ouvertPar()).isEqualTo(OUVREUR);

        compter(inventaire, "ELEC-001", 10, MotifAjustementStock.CASSE, "Deux écrans fissurés");   // écart -2
        compter(inventaire, "ALIM-001", 185, MotifAjustementStock.ECART_DE_COMPTAGE, null);          // écart +5
        compter(inventaire, "MOB-001", 18, null, null);                                              // aucun écart : pas de motif
        InventaireResponse soumis = inventaireService.soumettre(inventaire.id(), OUVREUR);
        assertThat(soumis.statut()).isEqualTo(StatutInventaire.SOUMIS);
        assertThat(soumis.nombreComptees()).isEqualTo(3);
        assertThat(soumis.nombreEcarts()).isEqualTo(2);
        assertThat(soumis.valeurEcartTotal()).as("-2 × 189 000 + 5 × 900").isEqualByComparingTo("-373500");

        InventaireResponse valide = inventaireService.valider(inventaire.id(), VALIDATEUR);

        assertThat(valide.statut()).isEqualTo(StatutInventaire.VALIDE);
        assertThat(valide.validePar()).isEqualTo(VALIDATEUR);
        assertThat(article("ELEC-001").getQuantiteStock()).isEqualByComparingTo("10");
        assertThat(article("ALIM-001").getQuantiteStock()).isEqualByComparingTo("185");
        assertThat(article("MOB-001").getQuantiteStock()).as("compté sans écart : inchangé").isEqualByComparingTo("18");
        assertThat(article("ELEC-002").getQuantiteStock()).as("non compté : jamais touché").isEqualByComparingTo("5");

        List<MouvementStock> mouvements = mouvementRepository.findAll().stream()
            .filter(mouvement -> mouvement.getInventaire() != null && mouvement.getInventaire().getId().equals(inventaire.id())).toList();
        assertThat(mouvements).hasSize(2);
        assertThat(mouvements).filteredOn(m -> m.getType() == TypeMouvementStock.AJUSTEMENT_NEGATIF).singleElement().satisfies(m -> {
            assertThat(m.getQuantite()).isEqualByComparingTo("2");
            assertThat(m.getStockAvant()).isEqualByComparingTo("12");
            assertThat(m.getStockApres()).isEqualByComparingTo("10");
            assertThat(m.getCoutUnitaire()).isEqualByComparingTo("189000");
            assertThat(m.getCommentaire()).contains("Casse").contains("Deux écrans fissurés");
            assertThat(m.getEffectuePar().getUsername()).isEqualTo(VALIDATEUR);
        });
        assertThat(mouvements).filteredOn(m -> m.getType() == TypeMouvementStock.AJUSTEMENT_POSITIF).singleElement()
            .satisfies(m -> assertThat(m.getQuantite()).isEqualByComparingTo("5"));
        assertThat(journalRepository.findAll()).filteredOn(entree -> "VALIDATION".equals(entree.getAction()) && "INVENTAIRE".equals(entree.getEntite()))
            .singleElement().extracting(JournalActivite::getDetails).asString().contains("2 article(s) ajusté(s)").contains(OUVREUR);
        assertThat(inventaireService.lignes(inventaire.id(), true, 0, 50).lignes()).as("écarts appliqués")
            .extracting(ligne -> ligne.ecartApplique().intValueExact()).containsExactlyInAnyOrder(-2, 5);
    }

    @Test
    void lesVentesFaitesPendantLeComptageNeSontJamaisEcrasees() {
        InventaireResponse inventaire = ouvrirTout();                   // photographie : ELEC-001 = 12
        compter(inventaire, "ELEC-001", 11, MotifAjustementStock.VOL, null); // le comptage trouve 11 : écart -1
        inventaireService.soumettre(inventaire.id(), OUVREUR);

        // pendant ce temps, on vend 2 téléphones : le stock réel passe à 10
        sessionService.ouvrir(new OuvertureSessionRequest(null, new BigDecimal("1000")), "admin");
        VenteResponse brouillon = ventesService.creerVenteDirecte(new VenteDirecteRequest(null, false,
            List.of(new LigneDocumentRequest(article("ELEC-001").getId(), null, BigDecimal.valueOf(2), null, null)),
            BigDecimal.ZERO, ModeReglement.ESPECES, new BigDecimal("10000000")), "admin");
        ventesService.validerVente(brouillon.id(), "admin");
        assertThat(article("ELEC-001").getQuantiteStock()).isEqualByComparingTo("10");

        inventaireService.valider(inventaire.id(), VALIDATEUR);

        assertThat(article("ELEC-001").getQuantiteStock())
            .as("10 (stock du moment) + écart de -1, et non 11 (la valeur comptée qui aurait effacé la vente)").isEqualByComparingTo("9");
    }

    @Test
    void unEcartQuiMettraitLeStockSousZeroRefuseToutLaValidationSansRienChanger() {
        InventaireResponse inventaire = ouvrirTout();
        compter(inventaire, "ALIM-001", 185, MotifAjustementStock.ECART_DE_COMPTAGE, null);   // appliquerait +5
        compter(inventaire, "ELEC-001", 0, MotifAjustementStock.VOL, null);                    // appliquerait -12
        inventaireService.soumettre(inventaire.id(), OUVREUR);
        sessionService.ouvrir(new OuvertureSessionRequest(null, new BigDecimal("1000")), "admin");
        VenteResponse brouillon = ventesService.creerVenteDirecte(new VenteDirecteRequest(null, false,
            List.of(new LigneDocumentRequest(article("ELEC-001").getId(), null, BigDecimal.valueOf(5), null, null)),
            BigDecimal.ZERO, ModeReglement.ESPECES, new BigDecimal("10000000")), "admin");
        ventesService.validerVente(brouillon.id(), "admin");                                   // stock réel : 7

        assertThatThrownBy(() -> inventaireService.valider(inventaire.id(), VALIDATEUR))
            .isInstanceOf(ConflitRessourceException.class)
            .hasMessageContaining("Recomptez l'article ELEC-001");

        assertThat(article("ALIM-001").getQuantiteStock()).as("rien n'a été appliqué").isEqualByComparingTo("180");
        assertThat(article("ELEC-001").getQuantiteStock()).isEqualByComparingTo("7");
        assertThat(inventaireService.obtenir(inventaire.id()).statut()).isEqualTo(StatutInventaire.SOUMIS);
    }

    @Test
    void lInventaireEstValideParUneAutrePersonneQueCelleQuiAOuvert() {
        InventaireResponse inventaire = ouvrirTout();
        compter(inventaire, "ELEC-001", 10, MotifAjustementStock.CASSE, null);
        inventaireService.soumettre(inventaire.id(), OUVREUR);

        assertThatThrownBy(() -> inventaireService.valider(inventaire.id(), OUVREUR))
            .isInstanceOf(ConflitRessourceException.class)
            .hasMessageContaining("autre personne");
        assertThat(article("ELEC-001").getQuantiteStock()).isEqualByComparingTo("12");
    }

    @Test
    void unSeulInventairePeutEtreEnCoursALaFois() {
        InventaireResponse premier = ouvrirTout();

        assertThatThrownBy(this::ouvrirTout)
            .isInstanceOf(ConflitRessourceException.class)
            .hasMessageContaining("déjà en cours").hasMessageContaining(premier.code());

        inventaireService.annuler(premier.id(), VALIDATEUR);
        assertThat(inventaireService.obtenir(premier.id()).statut()).isEqualTo(StatutInventaire.ANNULE);
        assertThat(ouvrirTout().id()).as("la voie est libre après l'annulation").isNotEqualTo(premier.id());
        assertThat(article("ELEC-001").getQuantiteStock()).as("annuler ne touche pas au stock").isEqualByComparingTo("12");
    }

    @Test
    void toutEcartExigeUnMotifALaSoumission() {
        InventaireResponse inventaire = ouvrirTout();
        compter(inventaire, "ELEC-001", 10, null, null);
        compter(inventaire, "MOB-001", 17, MotifAjustementStock.AUTRE, null);

        assertThatThrownBy(() -> inventaireService.soumettre(inventaire.id(), OUVREUR))
            .isInstanceOf(ConflitRessourceException.class)
            .hasMessageContaining("motif").hasMessageContaining("ELEC-001").hasMessageContaining("MOB-001");

        compter(inventaire, "ELEC-001", 10, MotifAjustementStock.CASSE, null);
        compter(inventaire, "MOB-001", 17, MotifAjustementStock.AUTRE, "Échantillon offert à un client");
        assertThat(inventaireService.soumettre(inventaire.id(), OUVREUR).statut()).isEqualTo(StatutInventaire.SOUMIS);
    }

    @Test
    void onNeSoumetPasSansRienCompterEtOnNeSaisitPlusApresLaSoumissionSaufEnReprenant() {
        InventaireResponse inventaire = ouvrirTout();
        assertThatThrownBy(() -> inventaireService.soumettre(inventaire.id(), OUVREUR))
            .isInstanceOf(ConflitRessourceException.class).hasMessageContaining("au moins un article");

        compter(inventaire, "ELEC-001", 12, null, null);
        inventaireService.soumettre(inventaire.id(), OUVREUR);
        assertThatThrownBy(() -> compter(inventaire, "ELEC-001", 11, MotifAjustementStock.VOL, null))
            .isInstanceOf(ConflitRessourceException.class).hasMessageContaining("en saisie");

        assertThat(inventaireService.reprendre(inventaire.id(), VALIDATEUR).statut()).isEqualTo(StatutInventaire.EN_SAISIE);
        compter(inventaire, "ELEC-001", 11, MotifAjustementStock.VOL, null);
        assertThat(inventaireService.lignes(inventaire.id(), false, 0, 50).lignes())
            .filteredOn(ligne -> ligne.reference().equals("ELEC-001")).singleElement()
            .satisfies(ligne -> assertThat(ligne.stockCompte()).isEqualByComparingTo("11"));
    }

    @Test
    void unInventaireParCategorieNeContientQueLesArticlesDeCeRayon() {
        CategorieArticle alimentation = categorieRepository.findAll().stream()
            .filter(categorie -> categorie.getNom().equals("Alimentation")).findFirst().orElseThrow();

        InventaireResponse inventaire = inventaireService.ouvrir(
            new InventaireRequest(PerimetreInventaire.CATEGORIE, alimentation.getId(), "Rayon alimentation"), OUVREUR);

        assertThat(inventaire.nombreLignes()).isEqualTo(2);
        assertThat(inventaire.categorie()).isEqualTo("Alimentation");
        assertThat(inventaireService.lignes(inventaire.id(), false, 0, 50).lignes())
            .extracting(LigneInventaireResponse::reference).containsExactlyInAnyOrder("ALIM-001", "ALIM-002");
        assertThatThrownBy(() -> compter(inventaire, "ELEC-001", 1, MotifAjustementStock.VOL, null))
            .hasMessageContaining("ne fait pas partie");
    }

    @Test
    void unPerimetreParCategorieExigeUneCategorie() {
        assertThatThrownBy(() -> inventaireService.ouvrir(new InventaireRequest(PerimetreInventaire.CATEGORIE, null, null), OUVREUR))
            .isInstanceOf(RequeteInvalideException.class).hasMessageContaining("catégorie");
    }
}
