package com.gesstock.gesstock.ventes;

import com.gesstock.gesstock.common.exception.PrixSousLeCoutException;
import com.gesstock.gesstock.common.exception.RequeteInvalideException;
import com.gesstock.gesstock.entities.Article;
import com.gesstock.gesstock.entities.ModeReglement;
import com.gesstock.gesstock.entities.StatutVente;
import com.gesstock.gesstock.referentiels.dto.OuvertureSessionRequest;
import com.gesstock.gesstock.referentiels.service.SessionCaisseService;
import com.gesstock.gesstock.repositories.ArticleRepository;
import com.gesstock.gesstock.repositories.JournalActiviteRepository;
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
 * Demande du client (novembre 2026) : le caissier fixe seul le prix négocié, quel que soit le taux, par unité ou pour la
 * ligne entière ; les quantités ont des décimales. Décision du propriétaire (novembre 2026) : un prix négocié sous le prix
 * d'achat (coût moyen) empêche la validation de la vente. Données de démonstration : ELEC-001 à 245 000 (coût 189 000,
 * stock 12), ALIM-001 riz à 1 300 (coût 900).
 */
@SpringBootTest
@Transactional
class RemisesLibresEtQuantitesDecimalesTests {

    private static final String CAISSIER = "caissier";

    @Autowired
    private GestionVentesService ventesService;
    @Autowired
    private SessionCaisseService sessionService;
    @Autowired
    private ArticleRepository articleRepository;
    @Autowired
    private JournalActiviteRepository journalRepository;

    private Article article(String reference) {
        return articleRepository.findByReferenceIgnoreCase(reference).orElseThrow();
    }

    private VenteResponse vendre(LigneDocumentRequest ligne) {
        sessionService.ouvrir(new OuvertureSessionRequest(LocalDate.now(), new BigDecimal("1000")), "admin");
        VenteResponse brouillon = ventesService.creerVenteDirecte(new VenteDirecteRequest(null, false, List.of(ligne),
            BigDecimal.ZERO, ModeReglement.ESPECES, new BigDecimal("10000000")), CAISSIER);
        return ventesService.validerVente(brouillon.id(), CAISSIER);
    }

    @Test
    void unCaissierAccordeSeulUneRemiseDeMoitieSansAccordDeResponsable() {
        VenteResponse vente = vendre(new LigneDocumentRequest(article("ELEC-001").getId(), null, BigDecimal.ONE,
            new BigDecimal("200000"), null));

        assertThat(vente.montantTotal()).isEqualByComparingTo("200000");
        assertThat(vente.avertissements()).as("200 000 reste au-dessus du coût de 189 000").isEmpty();
    }

    @Test
    void laRemiseSurLeTotalDeLaLigneDonneExactementLeTotalNegocie() {
        // 3 téléphones (735 000 au catalogue) cédés 700 000 pour les trois : 233 333,33… l'unité, mais 700 000 tout rond
        VenteResponse vente = vendre(new LigneDocumentRequest(article("ELEC-001").getId(), null, BigDecimal.valueOf(3),
            null, null, new BigDecimal("700000")));

        assertThat(vente.montantTotal()).isEqualByComparingTo("700000");
        assertThat(vente.lignes().get(0).montantLigne()).isEqualByComparingTo("700000");
        assertThat(vente.lignes().get(0).prixUnitaire()).isEqualByComparingTo("233333.3333");
    }

    @Test
    void leTotalNegocieNePeutPasDepasserLeTotalAuPrixCatalogue() {
        sessionService.ouvrir(new OuvertureSessionRequest(LocalDate.now(), new BigDecimal("1000")), "admin");
        assertThatThrownBy(() -> ventesService.creerVenteDirecte(new VenteDirecteRequest(null, false,
                List.of(new LigneDocumentRequest(article("ELEC-001").getId(), null, BigDecimal.ONE, null, null, new BigDecimal("250000"))),
                BigDecimal.ZERO, ModeReglement.ESPECES, new BigDecimal("10000000")), CAISSIER))
            .isInstanceOf(RequeteInvalideException.class)
            .hasMessageContaining("ne peut pas dépasser");
    }

    @Test
    void uneQuantiteAVirguleSortExactementDuStock() {
        Article riz = article("ALIM-001");
        BigDecimal stockAvant = riz.getQuantiteStock();

        VenteResponse vente = vendre(new LigneDocumentRequest(riz.getId(), null, new BigDecimal("2.5"), null, null));

        assertThat(vente.montantTotal()).isEqualByComparingTo("3250");          // 2,5 × 1 300
        assertThat(article("ALIM-001").getQuantiteStock()).isEqualByComparingTo(stockAvant.subtract(new BigDecimal("2.5")));
    }

    private VenteResponse brouillon(LigneDocumentRequest... lignes) {
        sessionService.ouvrir(new OuvertureSessionRequest(LocalDate.now(), new BigDecimal("1000")), "admin");
        return ventesService.creerVenteDirecte(new VenteDirecteRequest(null, false, List.of(lignes),
            BigDecimal.ZERO, ModeReglement.ESPECES, new BigDecimal("10000000")), CAISSIER);
    }

    @Test
    void unPrixNegocieSousLePrixDAchatEmpecheLaValidation() {
        Article telephone = article("ELEC-001");
        BigDecimal stockAvant = telephone.getQuantiteStock();
        VenteResponse brouillon = brouillon(new LigneDocumentRequest(telephone.getId(), null, BigDecimal.ONE,
            new BigDecimal("150000"), null));
        long entreesDuJournalAvant = journalRepository.count();

        assertThatThrownBy(() -> ventesService.validerVente(brouillon.id(), CAISSIER))
            .isInstanceOf(PrixSousLeCoutException.class)
            .hasMessageContaining("sous le prix d'achat").hasMessageContaining("ELEC-001")
            .hasMessageContaining("150000").hasMessageContaining("minimum 189000");

        assertThat(ventesService.obtenirVente(brouillon.id()).statut()).as("la vente reste un brouillon").isEqualTo(StatutVente.BROUILLON);
        assertThat(article("ELEC-001").getQuantiteStock()).as("aucun article n'est sorti du stock").isEqualByComparingTo(stockAvant);
        assertThat(journalRepository.count()).as("un refus ne laisse aucune trace au journal").isEqualTo(entreesDuJournalAvant);
    }

    @Test
    void unPrixNegocieEgalAuPrixDAchatPasse() {
        VenteResponse vente = vendre(new LigneDocumentRequest(article("ELEC-001").getId(), null, BigDecimal.ONE,
            new BigDecimal("189000"), null));

        assertThat(vente.statut()).isEqualTo(StatutVente.VALIDEE);
        assertThat(vente.avertissements()).as("au coût exact : ni refus ni alerte").isEmpty();
    }

    @Test
    void lePrixNegocieSurLeTotalDeLaLigneEstControleLuiAussi() {
        // 3 téléphones coûtent 567 000 : 500 000 pour les trois est refusé, 567 000 passe
        Article telephone = article("ELEC-001");
        VenteResponse sousLeCout = brouillon(new LigneDocumentRequest(telephone.getId(), null, BigDecimal.valueOf(3),
            null, null, new BigDecimal("500000")));
        assertThatThrownBy(() -> ventesService.validerVente(sousLeCout.id(), CAISSIER))
            .isInstanceOf(PrixSousLeCoutException.class).hasMessageContaining("minimum 567000");

        VenteResponse auCout = ventesService.creerVenteDirecte(new VenteDirecteRequest(null, false,
            List.of(new LigneDocumentRequest(telephone.getId(), null, BigDecimal.valueOf(3), null, null, new BigDecimal("567000"))),
            BigDecimal.ZERO, ModeReglement.ESPECES, new BigDecimal("10000000")), CAISSIER);
        assertThat(ventesService.validerVente(auCout.id(), CAISSIER).statut()).isEqualTo(StatutVente.VALIDEE);
    }

    @Test
    void leRefusCiteTousLesArticlesFautifsEnUneFois() {
        VenteResponse brouillon = brouillon(
            new LigneDocumentRequest(article("ELEC-001").getId(), null, BigDecimal.ONE, new BigDecimal("150000"), null),
            new LigneDocumentRequest(article("ALIM-001").getId(), null, BigDecimal.ONE, new BigDecimal("800"), null));

        assertThatThrownBy(() -> ventesService.validerVente(brouillon.id(), CAISSIER))
            .isInstanceOf(PrixSousLeCoutException.class)
            .hasMessageContaining("ELEC-001").hasMessageContaining("ALIM-001");
    }

    @Test
    void unArticleDontLeCoutEstEncoreAZeroNeBloqueJamais() {
        Article riz = article("ALIM-001");
        riz.setCmp(BigDecimal.ZERO);
        articleRepository.saveAndFlush(riz);

        VenteResponse vente = vendre(new LigneDocumentRequest(riz.getId(), null, BigDecimal.ONE, new BigDecimal("1"), null));

        assertThat(vente.statut()).isEqualTo(StatutVente.VALIDEE);
    }
}
