package com.gesstock.gesstock.ventes;

import com.gesstock.gesstock.common.exception.ConflitRessourceException;
import com.gesstock.gesstock.entities.Article;
import com.gesstock.gesstock.entities.ModeReglement;
import com.gesstock.gesstock.entities.StatutProforma;
import com.gesstock.gesstock.referentiels.dto.OuvertureSessionRequest;
import com.gesstock.gesstock.referentiels.dto.SessionCaisseResponse;
import com.gesstock.gesstock.referentiels.service.SessionCaisseService;
import com.gesstock.gesstock.repositories.ArticleRepository;
import com.gesstock.gesstock.statistiques.dto.SessionCaisseRapportResponse;
import com.gesstock.gesstock.statistiques.service.StatistiquesService;
import com.gesstock.gesstock.ventes.dto.LigneDocumentRequest;
import com.gesstock.gesstock.ventes.dto.ProformaRequest;
import com.gesstock.gesstock.ventes.dto.ProformaResponse;
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
 * Chaque franc encaissé doit être attendu dans la caisse, et chaque encaissement affiché doit être le vrai.
 * (Ces trois défauts ont été mesurés avant correction : cash invisible après conversion d'un proforma, « montant encaissé »
 * égal à la somme remise par le client, et vente « au comptant » acceptée sans aucun règlement.)
 */
@SpringBootTest
@Transactional
class IntegriteDeLaCaisseTests {

    private static final String ADMIN = "admin";

    @Autowired
    private GestionVentesService ventesService;
    @Autowired
    private SessionCaisseService sessionService;
    @Autowired
    private StatistiquesService statistiquesService;
    @Autowired
    private ArticleRepository articleRepository;

    private Article telephone() {
        return articleRepository.findByReferenceIgnoreCase("ELEC-001").orElseThrow();
    }

    private SessionCaisseResponse ouvrirSession() {
        return sessionService.ouvrir(new OuvertureSessionRequest(null, new BigDecimal("1000")), ADMIN);
    }

    private ProformaResponse proformaAccepte(String numero) {
        ProformaResponse proforma = ventesService.creerProforma(new ProformaRequest(numero, LocalDate.now(),
            LocalDate.now().plusDays(3), null,
            List.of(new LigneDocumentRequest(telephone().getId(), null, 1, null, null))), ADMIN);
        ventesService.modifierStatutProforma(proforma.id(), StatutProforma.ENVOYEE, ADMIN);
        return ventesService.modifierStatutProforma(proforma.id(), StatutProforma.ACCEPTEE, ADMIN);
    }

    @Test
    void leCashDUnProformaConvertiAuComptantEntreDansLeSoldeTheorique() {
        SessionCaisseResponse session = ouvrirSession();
        ProformaResponse proforma = proformaAccepte("PF-CASH-1");

        VenteResponse vente = ventesService.convertirProformaEnVente(
            proforma.id(), false, ModeReglement.ESPECES, new BigDecimal("250000"), ADMIN);

        assertThat(vente.modeReglement()).isEqualTo(ModeReglement.ESPECES);
        assertThat(vente.montantRecu()).isEqualByComparingTo("250000");
        assertThat(vente.montantHT()).as("HT du reçu").isEqualByComparingTo("245000");
        assertThat(sessionService.obtenir(session.id()).soldeTheorique()).isEqualByComparingTo("246000"); // 1 000 + 245 000
    }

    @Test
    void uneConversionAuComptantSansReglementEstRefuseeEtRienNeChange() {
        SessionCaisseResponse session = ouvrirSession();
        ProformaResponse proforma = proformaAccepte("PF-CASH-2");
        int stockAvant = telephone().getQuantiteStock();

        assertThatThrownBy(() -> ventesService.convertirProformaEnVente(proforma.id(), false, null, null, ADMIN))
            .isInstanceOf(ConflitRessourceException.class)
            .hasMessageContaining("mode de règlement");
        assertThatThrownBy(() -> ventesService.convertirProformaEnVente(
            proforma.id(), false, ModeReglement.ESPECES, new BigDecimal("100000"), ADMIN))
            .isInstanceOf(ConflitRessourceException.class)
            .hasMessageContaining("couvrir");

        assertThat(telephone().getQuantiteStock()).isEqualTo(stockAvant);
        assertThat(sessionService.obtenir(session.id()).soldeTheorique()).isEqualByComparingTo("1000");
    }

    @Test
    void uneVenteDirecteAuComptantSansModeDeReglementEstRefusee() {
        ouvrirSession();

        assertThatThrownBy(() -> ventesService.creerVenteDirecte(new VenteDirecteRequest(null, false,
            List.of(new LigneDocumentRequest(telephone().getId(), null, 1, null, null)),
            BigDecimal.ZERO, null, null), ADMIN))
            .isInstanceOf(ConflitRessourceException.class)
            .hasMessageContaining("exige un mode de règlement");
    }

    @Test
    void leMontantEncaisseDuRapportEstLeTotalDeLaVentePasLaSommeRemiseParLeClient() {
        SessionCaisseResponse session = ouvrirSession();
        VenteResponse brouillon = ventesService.creerVenteDirecte(new VenteDirecteRequest(null, false,
            List.of(new LigneDocumentRequest(telephone().getId(), null, 1, null, null)),
            BigDecimal.ZERO, ModeReglement.ESPECES, new BigDecimal("1000000")), ADMIN); // il donne un billet de 1 000 000
        ventesService.validerVente(brouillon.id(), ADMIN);

        SessionCaisseRapportResponse ligne = statistiquesService.rapport(LocalDate.now(), LocalDate.now()).sessionsCaisse()
            .stream().filter(s -> s.sessionId().equals(session.id())).findFirst().orElseThrow();

        assertThat(ligne.chiffreAffaires()).isEqualByComparingTo("245000");
        assertThat(ligne.montantEncaisse()).as("avant correction : 1 000 000").isEqualByComparingTo("245000");
    }
}
