package com.gesstock.gesstock.ventes;

import com.gesstock.gesstock.common.exception.RequeteInvalideException;
import com.gesstock.gesstock.entities.Article;
import com.gesstock.gesstock.entities.ModeReglement;
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
 * ligne entière ; une vente sous le prix d'achat passe mais est signalée et notée au journal ; les quantités ont des
 * décimales. Données de démonstration : ELEC-001 à 245 000 (coût 189 000, stock 12), ALIM-001 riz à 1 300 (coût 900).
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

    @Test
    void uneVenteSousLePrixDAchatPasseMaisEstSignaleeEtNoteeAuJournal() {
        VenteResponse vente = vendre(new LigneDocumentRequest(article("ELEC-001").getId(), null, BigDecimal.ONE,
            new BigDecimal("150000"), null));

        assertThat(vente.montantTotal()).isEqualByComparingTo("150000");
        assertThat(vente.avertissements()).singleElement().asString()
            .contains("sous le prix d'achat").contains("ELEC-001").contains("189000");
        assertThat(journalRepository.findAll())
            .anySatisfy(entree -> {
                assertThat(entree.getAction()).isEqualTo("VENTE_SOUS_COUT");
                assertThat(entree.getDetails()).contains(vente.code()).contains("ELEC-001");
            });
    }
}
