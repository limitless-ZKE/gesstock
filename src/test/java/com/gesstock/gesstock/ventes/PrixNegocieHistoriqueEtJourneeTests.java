package com.gesstock.gesstock.ventes;

import com.gesstock.gesstock.common.exception.ConflitRessourceException;
import com.gesstock.gesstock.common.exception.RequeteInvalideException;
import com.gesstock.gesstock.entities.Article;
import com.gesstock.gesstock.entities.ModeReglement;
import com.gesstock.gesstock.entities.SessionCaisse;
import com.gesstock.gesstock.entities.StatutProforma;
import com.gesstock.gesstock.entities.StatutSessionCaisse;
import com.gesstock.gesstock.referentiels.dto.OuvertureSessionRequest;
import com.gesstock.gesstock.referentiels.dto.SessionCaisseResponse;
import com.gesstock.gesstock.referentiels.service.SessionCaisseService;
import com.gesstock.gesstock.repositories.ArticleRepository;
import com.gesstock.gesstock.repositories.SessionCaisseRepository;
import com.gesstock.gesstock.repositories.UtilisateurRepository;
import com.gesstock.gesstock.ventes.dto.LigneDocumentRequest;
import com.gesstock.gesstock.ventes.dto.LigneDocumentResponse;
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
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Les données de démonstration (admin, ELEC-001 à 245000, stock 12) viennent du DataSeeder. */
@SpringBootTest
@Transactional
class PrixNegocieHistoriqueEtJourneeTests {

    private static final String ADMIN = "admin";

    @Autowired
    private GestionVentesService ventesService;
    @Autowired
    private SessionCaisseService sessionService;
    @Autowired
    private ArticleRepository articleRepository;
    @Autowired
    private SessionCaisseRepository sessionRepository;
    @Autowired
    private UtilisateurRepository utilisateurRepository;

    private Article telephone() {
        return articleRepository.findByReferenceIgnoreCase("ELEC-001").orElseThrow();
    }

    private SessionCaisseResponse ouvrirSession() {
        return sessionService.ouvrir(new OuvertureSessionRequest(null, new BigDecimal("1000")), ADMIN);
    }

    private VenteResponse venteDirecte(int quantite, String prixNegocie) {
        return ventesService.creerVenteDirecte(new VenteDirecteRequest(
            null, false,
            List.of(new LigneDocumentRequest(telephone().getId(), null, BigDecimal.valueOf(quantite),
                prixNegocie == null ? null : new BigDecimal(prixNegocie), null)),
            BigDecimal.ZERO, ModeReglement.ESPECES, new BigDecimal("10000000")), ADMIN);
    }

    // --- Prix négocié en vente directe ---

    @Test
    void prixNegocieEstAppliqueEtLaRemiseNegocieeResteCalculable() {
        ouvrirSession();

        VenteResponse vente = ventesService.validerVente(venteDirecte(2, "220000.50").id(), ADMIN);

        LigneDocumentResponse ligne = vente.lignes().get(0);
        assertThat(ligne.prixUnitaire()).isEqualByComparingTo("220000.50");
        assertThat(ligne.prixCatalogue()).isEqualByComparingTo("245000");
        // remise négociée par unité = catalogue - prix négocié ; le total ne tient compte que du prix négocié
        assertThat(ligne.prixCatalogue().subtract(ligne.prixUnitaire())).isEqualByComparingTo("24999.50");
        assertThat(vente.montantTotal()).isEqualByComparingTo("440001.00");
    }

    @Test
    void sansPrixNegocieLePrixCatalogueEstAppliqueEtAucuneRemiseNEstAffichee() {
        VenteResponse vente = venteDirecte(1, null);

        LigneDocumentResponse ligne = vente.lignes().get(0);
        assertThat(ligne.prixUnitaire()).isEqualByComparingTo("245000");
        assertThat(ligne.prixCatalogue()).isEqualByComparingTo(ligne.prixUnitaire());
    }

    @Test
    void prixNegocieSuperieurAuPrixCatalogueEstRefuse() {
        assertThatThrownBy(() -> venteDirecte(1, "245000.01"))
            .isInstanceOf(RequeteInvalideException.class)
            .hasMessageContaining("prix catalogue");
    }

    @Test
    void prixNegocieDUnProformaSuitLaConversionEnVente() {
        ouvrirSession();
        ProformaResponse proforma = ventesService.creerProforma(new ProformaRequest(
            "PF-NEGO-1", LocalDate.now(), LocalDate.now().plusDays(7), null,
            List.of(new LigneDocumentRequest(telephone().getId(), null, BigDecimal.valueOf(1), new BigDecimal("200000"), null))), ADMIN);
        assertThat(proforma.lignes().get(0).prixCatalogue()).isEqualByComparingTo("245000");
        ventesService.modifierStatutProforma(proforma.id(), StatutProforma.ENVOYEE, ADMIN);
        ventesService.modifierStatutProforma(proforma.id(), StatutProforma.ACCEPTEE, ADMIN);

        VenteResponse vente = ventesService.convertirProformaEnVente(proforma.id(), false, ModeReglement.ESPECES, new BigDecimal("1000000"), ADMIN);

        assertThat(vente.lignes().get(0).prixUnitaire()).isEqualByComparingTo("200000");
        assertThat(vente.lignes().get(0).prixCatalogue()).isEqualByComparingTo("245000");
    }

    // --- Historique des ventes d'une journée (réimpression du reçu) ---

    @Test
    void historiqueDUneJourneeListeLesVentesAvecLeVendeurEtPermetLaReimpression() {
        ouvrirSession();
        VenteResponse vente = ventesService.validerVente(venteDirecte(1, "230000").id(), ADMIN);
        venteDirecte(1, null); // un brouillon : il n'a pas de date de vente, il n'est pas dans l'historique

        List<VenteResponse> duJour = ventesService.listerVentes(LocalDate.now());
        List<VenteResponse> hier = ventesService.listerVentes(LocalDate.now().minusDays(1));

        assertThat(duJour).extracting(VenteResponse::id).containsExactly(vente.id());
        assertThat(duJour.get(0).vendeur()).isEqualTo(ADMIN);
        assertThat(hier).extracting(VenteResponse::id).doesNotContain(vente.id());
        // toutes les informations du reçu sont dans la réponse : une réimpression reproduit le ticket d'origine
        assertThat(duJour.get(0).lignes().get(0).prixCatalogue()).isEqualByComparingTo("245000");
        assertThat(duJour.get(0).code()).isEqualTo(vente.code());
        assertThat(duJour.get(0).dateVente()).isBetween(LocalDateTime.now().minusMinutes(5), LocalDateTime.now().plusMinutes(5));
    }

    // --- Date de la journée imposée par le serveur ---

    @Test
    void laDateDeLaJourneeEstCelleDuServeurEtNeSeChoisitPas() {
        assertThatThrownBy(() -> sessionService.ouvrir(
            new OuvertureSessionRequest(LocalDate.now().plusDays(3), BigDecimal.ZERO), ADMIN))
            .isInstanceOf(RequeteInvalideException.class)
            .hasMessageContaining("fixée par le serveur");
        assertThatThrownBy(() -> sessionService.ouvrir(
            new OuvertureSessionRequest(LocalDate.now().minusDays(1), BigDecimal.ZERO), ADMIN))
            .isInstanceOf(RequeteInvalideException.class);

        // sans date, ou avec la date du jour, l'ouverture se fait à la date du serveur
        SessionCaisseResponse session = ouvrirSession();
        assertThat(session.journee()).isEqualTo(LocalDate.now());
        assertThat(session.journeeDepassee()).isFalse();
    }

    @Test
    void uneJourneeDeLaVeilleNonClotureeBloqueLOuvertureEtLesVentes() {
        SessionCaisse restee = new SessionCaisse();
        restee.setJournee(LocalDate.now().minusDays(1));
        restee.setCode("CAISSE-VEILLE");
        restee.setOuverteLe(LocalDateTime.now().minusDays(1));
        restee.setFondDeCaisse(new BigDecimal("1000"));
        restee.setStatut(StatutSessionCaisse.OUVERTE);
        restee.setOuvertePar(utilisateurRepository.findByUsername(ADMIN).orElseThrow());
        sessionRepository.saveAndFlush(restee);

        // l'écran de caisse la retrouve et la signale comme dépassée
        SessionCaisseResponse courante = sessionService.sessionOuverteCourante();
        assertThat(courante.id()).isEqualTo(restee.getId());
        assertThat(courante.journeeDepassee()).isTrue();

        // on ne peut pas ouvrir celle d'aujourd'hui avant d'avoir clôturé la veille
        assertThatThrownBy(this::ouvrirSession)
            .isInstanceOf(ConflitRessourceException.class)
            .hasMessageContaining("n'a pas été clôturée");
        VenteResponse brouillon = venteDirecte(1, null);
        assertThatThrownBy(() -> ventesService.validerVente(brouillon.id(), ADMIN))
            .isInstanceOf(ConflitRessourceException.class)
            .hasMessageContaining(restee.getJournee().toString());

        // une fois la veille clôturée, la journée du jour peut s'ouvrir
        sessionService.cloturer(restee.getId(),
            new com.gesstock.gesstock.referentiels.dto.ClotureSessionRequest(new BigDecimal("1000")), ADMIN);
        assertThat(ouvrirSession().journee()).isEqualTo(LocalDate.now());
    }
}
