package com.gesstock.gesstock.ventes;

import com.gesstock.gesstock.common.exception.RequeteInvalideException;
import com.gesstock.gesstock.entities.ModeReglement;
import com.gesstock.gesstock.entities.StatutVente;
import com.gesstock.gesstock.referentiels.dto.OuvertureSessionRequest;
import com.gesstock.gesstock.referentiels.service.SessionCaisseService;
import com.gesstock.gesstock.repositories.ArticleRepository;
import com.gesstock.gesstock.ventes.dto.HistoriqueVentesResponse;
import com.gesstock.gesstock.ventes.dto.LigneDocumentRequest;
import com.gesstock.gesstock.ventes.dto.LigneHistoriqueVente;
import com.gesstock.gesstock.ventes.dto.VenteDirecteRequest;
import com.gesstock.gesstock.ventes.dto.VenteResponse;
import com.gesstock.gesstock.ventes.service.GestionVentesService;
import com.gesstock.gesstock.ventes.service.HistoriqueVentesService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** L'historique d'une période donne à l'écran et aux documents imprimés les mêmes chiffres, ventes annulées exclues des totaux. */
@SpringBootTest
@Transactional
class HistoriqueVentesPeriodeTests {

    private static final String ADMIN = "admin";

    @Autowired
    private HistoriqueVentesService historiqueService;
    @Autowired
    private GestionVentesService ventesService;
    @Autowired
    private SessionCaisseService sessionService;
    @Autowired
    private ArticleRepository articleRepository;

    private VenteResponse vendre(String reference, int quantite, ModeReglement mode) {
        var article = articleRepository.findByReferenceIgnoreCase(reference).orElseThrow();
        // en espèces on remet un gros billet ; un autre moyen de paiement doit correspondre exactement au total
        BigDecimal recu = mode == ModeReglement.ESPECES ? new BigDecimal("1000000") : article.getPrix().multiply(BigDecimal.valueOf(quantite));
        VenteResponse brouillon = ventesService.creerVenteDirecte(new VenteDirecteRequest(null, false,
            List.of(new LigneDocumentRequest(article.getId(), null, BigDecimal.valueOf(quantite), null, null)), BigDecimal.ZERO, mode, recu), ADMIN);
        return ventesService.validerVente(brouillon.id(), ADMIN);
    }

    @Test
    void leResumeComptePourLesVentesValideesEtMetLesAnnuleesAPart() {
        sessionService.ouvrir(new OuvertureSessionRequest(null, new BigDecimal("1000")), ADMIN);
        vendre("ELEC-001", 1, ModeReglement.ESPECES);                                  // 245 000
        vendre("ALIM-001", 4, ModeReglement.CARTE);                                    // 5 200
        VenteResponse annulee = vendre("ALIM-002", 2, ModeReglement.ESPECES);          // 3 900, annulée ensuite
        ventesService.annulerVente(annulee.id(), "Erreur de saisie", true, ADMIN);

        HistoriqueVentesResponse historique = historiqueService.historique(LocalDate.now(), LocalDate.now());

        assertThat(historique.resume().nombreValidees()).isEqualTo(2);
        assertThat(historique.resume().chiffreAffaires()).isEqualByComparingTo("250200");
        assertThat(historique.resume().nombreAnnulees()).isEqualTo(1);
        assertThat(historique.resume().montantAnnule()).isEqualByComparingTo("3900");
        assertThat(historique.resume().remises()).isZero();
        assertThat(historique.ventes()).hasSize(3);
        LigneHistoriqueVente riz = historique.ventes().stream().filter(v -> v.montantTotal().compareTo(new BigDecimal("5200")) == 0).findFirst().orElseThrow();
        assertThat(riz.nombreArticles()).isEqualByComparingTo("4");
        assertThat(riz.modeReglement()).isEqualTo(ModeReglement.CARTE);
        assertThat(riz.vendeur()).isEqualTo(ADMIN);
        assertThat(riz.client()).as("vente au comptant sans client").isNull();
        assertThat(historique.ventes().stream().filter(v -> v.statut() == StatutVente.ANNULEE)).hasSize(1);
        assertThat(historique.ventes()).extracting(LigneHistoriqueVente::dateVente).isSortedAccordingTo(java.util.Comparator.reverseOrder());
    }

    @Test
    void uneVenteDAujourdhuiNApparaitPasDansUnePeriodePassee() {
        sessionService.ouvrir(new OuvertureSessionRequest(null, new BigDecimal("1000")), ADMIN);
        vendre("ELEC-001", 1, ModeReglement.ESPECES);

        HistoriqueVentesResponse hier = historiqueService.historique(LocalDate.now().minusDays(7), LocalDate.now().minusDays(1));
        assertThat(hier.ventes()).isEmpty();
        assertThat(hier.resume().nombreValidees()).isZero();
        assertThat(hier.resume().chiffreAffaires()).isZero();

        HistoriqueVentesResponse semaine = historiqueService.historique(LocalDate.now().minusDays(7), LocalDate.now());
        assertThat(semaine.ventes()).hasSize(1);
    }

    @Test
    void uneVenteEnBrouillonNeFaitPasPartieDeLHistorique() {
        sessionService.ouvrir(new OuvertureSessionRequest(null, new BigDecimal("1000")), ADMIN);
        Long article = articleRepository.findByReferenceIgnoreCase("ELEC-001").orElseThrow().getId();
        ventesService.creerVenteDirecte(new VenteDirecteRequest(null, false,
            List.of(new LigneDocumentRequest(article, null, BigDecimal.valueOf(1), null, null)), BigDecimal.ZERO, ModeReglement.ESPECES, new BigDecimal("250000")), ADMIN);

        assertThat(historiqueService.historique(LocalDate.now(), LocalDate.now()).ventes()).isEmpty();
    }

    @Test
    void uneTropGrandePeriodeEstRefuseeAvecUnMessageQuiDitQuoiFaire() {
        sessionService.ouvrir(new OuvertureSessionRequest(null, new BigDecimal("1000")), ADMIN);
        vendre("ELEC-001", 1, ModeReglement.ESPECES);
        vendre("ALIM-001", 1, ModeReglement.ESPECES);

        assertThatThrownBy(() -> historiqueService.charger(LocalDate.now(), LocalDate.now(), 1, "l'afficher"))
            .isInstanceOf(RequeteInvalideException.class)
            .hasMessageContaining("trop pour l'afficher")
            .hasMessageContaining("période plus courte");
    }

    @Test
    void laPeriodeDoitEtreCoherenteEtNePasDepasserUnAn() {
        LocalDate aujourdhui = LocalDate.now();
        assertThatThrownBy(() -> historiqueService.historique(aujourdhui, aujourdhui.minusDays(2)))
            .isInstanceOf(RequeteInvalideException.class).hasMessageContaining("date de fin");
        assertThatThrownBy(() -> historiqueService.historique(aujourdhui, null))
            .isInstanceOf(RequeteInvalideException.class);
        assertThatThrownBy(() -> historiqueService.historique(aujourdhui.minusDays(400), aujourdhui))
            .isInstanceOf(RequeteInvalideException.class).hasMessageContaining("366");
        assertThat(historiqueService.historique(aujourdhui.minusDays(365), aujourdhui)).isNotNull();
    }
}
