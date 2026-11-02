package com.gesstock.gesstock.ventes;

import com.gesstock.gesstock.common.exception.ConflitRessourceException;
import com.gesstock.gesstock.entities.Article;
import com.gesstock.gesstock.entities.ModeReglement;
import com.gesstock.gesstock.entities.StatutProforma;
import com.gesstock.gesstock.entities.StatutVente;
import com.gesstock.gesstock.referentiels.dto.OuvertureSessionRequest;
import com.gesstock.gesstock.referentiels.service.SessionCaisseService;
import com.gesstock.gesstock.repositories.ArticleRepository;
import com.gesstock.gesstock.ventes.dto.LigneDocumentRequest;
import com.gesstock.gesstock.ventes.dto.ProformaRequest;
import com.gesstock.gesstock.ventes.dto.ProformaResponse;
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

/** Les données de démonstration (admin, ELEC-001 à 245000, stock 12) viennent du DataSeeder. */
@SpringBootTest
@Transactional
class ProformaServiceTests {

    private static final String ADMIN = "admin";

    @Autowired
    private GestionVentesService ventesService;
    @Autowired
    private SessionCaisseService sessionService;
    @Autowired
    private ArticleRepository articleRepository;

    private Article telephone() {
        return articleRepository.findByReferenceIgnoreCase("ELEC-001").orElseThrow();
    }

    private ProformaRequest proforma(String numero, int quantite) {
        return new ProformaRequest(numero, LocalDate.now(), LocalDate.now().plusDays(7), null,
            List.of(new LigneDocumentRequest(telephone().getId(), null, BigDecimal.valueOf(quantite), null, null)));
    }

    private ProformaResponse proformaAcceptee(String numero, int quantite) {
        ProformaResponse cree = ventesService.creerProforma(proforma(numero, quantite), ADMIN);
        ventesService.modifierStatutProforma(cree.id(), StatutProforma.ENVOYEE, ADMIN);
        return ventesService.modifierStatutProforma(cree.id(), StatutProforma.ACCEPTEE, ADMIN);
    }

    @Test
    void proformaCalculeSonTotalSansToucherAuStock() {
        BigDecimal stockAvant = telephone().getQuantiteStock();

        ProformaResponse cree = ventesService.creerProforma(proforma("PF-001", 3), ADMIN);

        assertThat(cree.statut()).isEqualTo(StatutProforma.BROUILLON);
        assertThat(cree.montantTotal()).isEqualByComparingTo("735000");
        assertThat(telephone().getQuantiteStock()).isEqualByComparingTo(stockAvant);
    }

    @Test
    void numeroDeProformaUniqueEtTransitionsDeStatutControlees() {
        ProformaResponse cree = ventesService.creerProforma(proforma("PF-002", 1), ADMIN);

        assertThatThrownBy(() -> ventesService.creerProforma(proforma("pf-002", 1), ADMIN))
            .isInstanceOf(ConflitRessourceException.class);
        // un brouillon ne peut pas être accepté sans avoir été envoyé
        assertThatThrownBy(() -> ventesService.modifierStatutProforma(cree.id(), StatutProforma.ACCEPTEE, ADMIN))
            .isInstanceOf(ConflitRessourceException.class);
    }

    @Test
    void proformaNonAccepteNePeutPasEtreConverti() {
        ProformaResponse cree = ventesService.creerProforma(proforma("PF-003", 1), ADMIN);

        assertThatThrownBy(() -> ventesService.convertirProformaEnVente(cree.id(), false, ModeReglement.ESPECES, new BigDecimal("1000000"), ADMIN))
            .isInstanceOf(ConflitRessourceException.class);
    }

    @Test
    void conversionExigeUneJourneeOuverteEtLeStockResteIntactSansElle() {
        ProformaResponse acceptee = proformaAcceptee("PF-004", 2);
        BigDecimal stockAvant = telephone().getQuantiteStock();

        assertThatThrownBy(() -> ventesService.convertirProformaEnVente(acceptee.id(), false, ModeReglement.ESPECES, new BigDecimal("1000000"), ADMIN))
            .isInstanceOf(ConflitRessourceException.class)
            .hasMessageContaining("journée de caisse");
        assertThat(telephone().getQuantiteStock()).isEqualByComparingTo(stockAvant);
    }

    @Test
    void conversionCreeUneVenteValideeDestockeEtMarqueLeProformaConverti() {
        sessionService.ouvrir(new OuvertureSessionRequest(LocalDate.now(), new BigDecimal("1000")), ADMIN);
        ProformaResponse acceptee = proformaAcceptee("PF-005", 2);
        BigDecimal stockAvant = telephone().getQuantiteStock();

        VenteResponse vente = ventesService.convertirProformaEnVente(acceptee.id(), false, ModeReglement.ESPECES, new BigDecimal("1000000"), ADMIN);

        assertThat(vente.statut()).isEqualTo(StatutVente.VALIDEE);
        assertThat(vente.proformaId()).isEqualTo(acceptee.id());
        assertThat(telephone().getQuantiteStock()).isEqualByComparingTo(stockAvant.subtract(BigDecimal.valueOf(2)));
        assertThat(ventesService.obtenirProforma(acceptee.id()).statut()).isEqualTo(StatutProforma.CONVERTIE);

        // un proforma déjà converti ne peut pas l'être une seconde fois (pas de double déstockage)
        assertThatThrownBy(() -> ventesService.convertirProformaEnVente(acceptee.id(), false, ModeReglement.ESPECES, new BigDecimal("1000000"), ADMIN))
            .isInstanceOf(ConflitRessourceException.class);
        assertThat(telephone().getQuantiteStock()).isEqualByComparingTo(stockAvant.subtract(BigDecimal.valueOf(2)));
    }
}
