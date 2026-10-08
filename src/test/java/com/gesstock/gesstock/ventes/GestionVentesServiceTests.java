package com.gesstock.gesstock.ventes;

import com.gesstock.gesstock.common.exception.ConflitRessourceException;
import com.gesstock.gesstock.entities.Article;
import com.gesstock.gesstock.entities.ModeReglement;
import com.gesstock.gesstock.entities.StatutVente;
import com.gesstock.gesstock.referentiels.dto.OuvertureSessionRequest;
import com.gesstock.gesstock.referentiels.dto.SessionCaisseResponse;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Les données de démonstration (admin, ELEC-001 à 245000, stock 12) viennent du DataSeeder. */
@SpringBootTest
@Transactional
class GestionVentesServiceTests {

    private static final String ADMIN = "admin";

    @Autowired
    private GestionVentesService ventesService;
    @Autowired
    private SessionCaisseService sessionService;
    @Autowired
    private ArticleRepository articleRepository;
    @Autowired
    private VenteRepository venteRepository;

    private Article telephone() {
        return articleRepository.findByReferenceIgnoreCase("ELEC-001").orElseThrow();
    }

    private SessionCaisseResponse ouvrirSession() {
        return sessionService.ouvrir(
            new OuvertureSessionRequest(LocalDate.now(), new BigDecimal("1000")), ADMIN);
    }

    private VenteDirecteRequest vente(int quantite, String tauxTva) {
        return new VenteDirecteRequest(
            null,
            false,
            List.of(new LigneDocumentRequest(telephone().getId(), null, quantite, null, null)),
            new BigDecimal(tauxTva),
            ModeReglement.ESPECES,
            new BigDecimal("10000000")); // une vente au comptant doit être couverte par le montant reçu
    }

    @Test
    void parDefautAucuneTvaNEstFactureeEtLeTotalEstLeNetDesLignes() {
        VenteResponse brouillon = ventesService.creerVenteDirecte(vente(2, "0"), ADMIN);

        assertThat(brouillon.montantHT()).isEqualByComparingTo("490000");
        assertThat(brouillon.montantTva()).isEqualByComparingTo("0");
        assertThat(brouillon.montantTotal()).isEqualByComparingTo("490000");
        assertThat(brouillon.statut()).isEqualTo(StatutVente.BROUILLON);
    }

    @Test
    void validationDecrementeLeStockEtAlimenteLeChiffreDAffairesDeSession() {
        SessionCaisseResponse session = ouvrirSession();
        int stockAvant = telephone().getQuantiteStock();

        VenteResponse brouillon = ventesService.creerVenteDirecte(vente(2, "0"), ADMIN);
        VenteResponse validee = ventesService.validerVente(brouillon.id(), ADMIN);

        assertThat(validee.statut()).isEqualTo(StatutVente.VALIDEE);
        assertThat(telephone().getQuantiteStock()).isEqualTo(stockAvant - 2);
        assertThat(venteRepository.chiffreAffairesSession(session.id(), StatutVente.VALIDEE))
            .isEqualByComparingTo("490000");
        assertThat(venteRepository.chiffreAffairesEntre(StatutVente.VALIDEE,
            LocalDateTime.now().minusMinutes(5), LocalDateTime.now().plusMinutes(5)))
            .isGreaterThanOrEqualTo(new BigDecimal("490000"));
    }

    @Test
    void stockInsuffisantEstRefuseEtLeStockResteIntact() {
        ouvrirSession();
        int stockAvant = telephone().getQuantiteStock();

        VenteResponse brouillon = ventesService.creerVenteDirecte(vente(stockAvant + 1, "0"), ADMIN);

        assertThatThrownBy(() -> ventesService.validerVente(brouillon.id(), ADMIN))
            .isInstanceOf(ConflitRessourceException.class)
            .hasMessageContaining("Stock insuffisant");
        assertThat(telephone().getQuantiteStock()).isEqualTo(stockAvant);
    }

    @Test
    void validationExigeUneSessionDeCaisseOuverte() {
        VenteResponse brouillon = ventesService.creerVenteDirecte(vente(1, "0"), ADMIN);

        assertThatThrownBy(() -> ventesService.validerVente(brouillon.id(), ADMIN))
            .isInstanceOf(ConflitRessourceException.class)
            .hasMessageContaining("journée de caisse");
    }

    @Test
    void ventesACreditExigentUnClient() {
        VenteDirecteRequest creditSansClient = new VenteDirecteRequest(
            null, true,
            List.of(new LigneDocumentRequest(telephone().getId(), null, 1, null, null)),
            BigDecimal.ZERO, null, null);

        assertThatThrownBy(() -> ventesService.creerVenteDirecte(creditSansClient, ADMIN))
            .isInstanceOf(ConflitRessourceException.class);
    }
}
