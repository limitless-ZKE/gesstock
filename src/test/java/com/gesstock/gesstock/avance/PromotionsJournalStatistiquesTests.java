package com.gesstock.gesstock.avance;

import com.gesstock.gesstock.common.exception.RessourceIntrouvableException;
import com.gesstock.gesstock.entities.Article;
import com.gesstock.gesstock.journal.dto.JournalActiviteResponse;
import com.gesstock.gesstock.journal.dto.RecapitulatifClotureResponse;
import com.gesstock.gesstock.journal.service.JournalActiviteService;
import com.gesstock.gesstock.promotions.dto.PromotionRequest;
import com.gesstock.gesstock.promotions.dto.PromotionResponse;
import com.gesstock.gesstock.promotions.exception.PromotionIntrouvableException;
import com.gesstock.gesstock.promotions.exception.PromotionInvalideException;
import com.gesstock.gesstock.promotions.service.PromotionService;
import com.gesstock.gesstock.referentiels.dto.ClotureSessionRequest;
import com.gesstock.gesstock.referentiels.dto.OuvertureSessionRequest;
import com.gesstock.gesstock.referentiels.dto.SessionCaisseResponse;
import com.gesstock.gesstock.referentiels.service.SessionCaisseService;
import com.gesstock.gesstock.repositories.ArticleRepository;
import com.gesstock.gesstock.statistiques.dto.SyntheseStatistiquesResponse;
import com.gesstock.gesstock.statistiques.service.StatistiquesService;
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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Les données de démonstration (admin, ELEC-001 à 245000, stock 12) viennent du DataSeeder. */
@SpringBootTest
@Transactional
class PromotionsJournalStatistiquesTests {

    private static final String ADMIN = "admin";

    @Autowired
    private PromotionService promotionService;
    @Autowired
    private JournalActiviteService journalService;
    @Autowired
    private StatistiquesService statistiquesService;
    @Autowired
    private GestionVentesService ventesService;
    @Autowired
    private SessionCaisseService sessionService;
    @Autowired
    private ArticleRepository articleRepository;

    private Article telephone() {
        return articleRepository.findByReferenceIgnoreCase("ELEC-001").orElseThrow();
    }

    private PromotionRequest promotion(String nom, String pourcentage, LocalDateTime debut, LocalDateTime fin) {
        return new PromotionRequest(nom, new BigDecimal(pourcentage), debut, fin, true, Set.of(telephone().getId()));
    }

    private VenteResponse venteValidee(int quantite) {
        VenteResponse brouillon = ventesService.creerVenteDirecte(new VenteDirecteRequest(
            null, false,
            List.of(new LigneDocumentRequest(telephone().getId(), null, quantite, null, null)),
            BigDecimal.ZERO, com.gesstock.gesstock.entities.ModeReglement.ESPECES, new BigDecimal("10000000")), ADMIN);
        return ventesService.validerVente(brouillon.id(), ADMIN);
    }

    // --- Promotions ---

    @Test
    void promotionCrudEtValidationDeLaPeriodeEtDesArticles() {
        LocalDateTime maintenant = LocalDateTime.now();
        PromotionResponse cree = promotionService.creer(
            promotion("Soldes", "10", maintenant.minusHours(1), maintenant.plusDays(1)));
        assertThat(cree.pourcentage()).isEqualByComparingTo("10");
        assertThat(cree.articleIds()).containsExactly(telephone().getId());

        assertThatThrownBy(() -> promotionService.creer(
            promotion("Période inversée", "10", maintenant.plusDays(2), maintenant)))
            .isInstanceOf(PromotionInvalideException.class);
        assertThatThrownBy(() -> promotionService.creer(new PromotionRequest(
            "Article inconnu", new BigDecimal("5"), maintenant, maintenant.plusDays(1), true, Set.of(-1L))))
            .isInstanceOf(PromotionIntrouvableException.class);

        promotionService.supprimer(cree.id());
        assertThatThrownBy(() -> promotionService.obtenir(cree.id()))
            .isInstanceOf(PromotionIntrouvableException.class);
    }

    @Test
    void promotionEnCoursEstAppliqueeALaVenteEtUnePromotionExpireeNon() {
        LocalDateTime maintenant = LocalDateTime.now();
        promotionService.creer(promotion("Expirée", "50", maintenant.minusDays(10), maintenant.minusDays(5)));
        assertThat(promotionService.promotionApplicable(telephone(), maintenant)).isNull();

        promotionService.creer(promotion("En cours", "10", maintenant.minusHours(1), maintenant.plusDays(1)));
        assertThat(promotionService.promotionApplicable(telephone(), maintenant)).isNotNull();

        sessionService.ouvrir(new OuvertureSessionRequest(LocalDate.now(), BigDecimal.ZERO), ADMIN);
        VenteResponse vente = venteValidee(1);
        assertThat(vente.montantTotal()).isEqualByComparingTo("220500"); // 245000 - 10 %
    }

    // --- Journal d'activités et récapitulatif de clôture ---

    @Test
    void journalTraceLesOperationsEtLeRecapitulatifResumeLaJournee() {
        SessionCaisseResponse session = sessionService.ouvrir(
            new OuvertureSessionRequest(LocalDate.now(), new BigDecimal("1000")), ADMIN);
        venteValidee(2);
        sessionService.cloturer(session.id(), new ClotureSessionRequest(new BigDecimal("1000")), ADMIN);

        assertThat(journalService.listerJournee(LocalDate.now()))
            .extracting(JournalActiviteResponse::action)
            .contains("OUVERTURE", "VALIDATION", "CLOTURE");

        RecapitulatifClotureResponse recap = journalService.recapitulatif(session.id());
        assertThat(recap.nombreVentesValidees()).isEqualTo(1);
        assertThat(recap.chiffreAffaires()).isEqualByComparingTo("490000");
        assertThat(recap.montantCloture()).isEqualByComparingTo("1000");
        assertThat(recap.activites()).extracting(JournalActiviteResponse::action).contains("OUVERTURE", "CLOTURE");
    }

    @Test
    void recapitulatifDUneSessionInconnueEstUneRessourceIntrouvable() {
        assertThatThrownBy(() -> journalService.recapitulatif(-1L))
            .isInstanceOf(RessourceIntrouvableException.class);
    }

    // --- Statistiques ---

    @Test
    void syntheseDonneArticleLePlusVenduChiffreDAffairesEtValeurDuStock() {
        BigDecimal valeurStockAvant = statistiquesService.synthese(LocalDate.now()).valeurStock();
        assertThat(valeurStockAvant).isPositive();

        sessionService.ouvrir(new OuvertureSessionRequest(LocalDate.now(), BigDecimal.ZERO), ADMIN);
        venteValidee(3);

        SyntheseStatistiquesResponse synthese = statistiquesService.synthese(LocalDate.now());
        assertThat(synthese.articleLePlusVendu()).isNotNull();
        assertThat(synthese.articleLePlusVendu().reference()).isEqualTo("ELEC-001");
        assertThat(synthese.articleLePlusVendu().quantiteVendue()).isEqualTo(3);
        assertThat(synthese.chiffreAffairesJour()).isEqualByComparingTo("735000");
        assertThat(synthese.chiffreAffairesMois()).isGreaterThanOrEqualTo(synthese.chiffreAffairesJour());
        assertThat(synthese.valeurStock()).isLessThan(valeurStockAvant); // 3 téléphones sont sortis du stock
    }
}
