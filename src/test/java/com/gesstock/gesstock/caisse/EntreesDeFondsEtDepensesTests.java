package com.gesstock.gesstock.caisse;

import com.gesstock.gesstock.approvisionnements.dto.ApprovisionnementRequest;
import com.gesstock.gesstock.approvisionnements.dto.ApprovisionnementResponse;
import com.gesstock.gesstock.approvisionnements.dto.FournisseurRequest;
import com.gesstock.gesstock.approvisionnements.dto.FournisseurResponse;
import com.gesstock.gesstock.approvisionnements.dto.LigneApprovisionnementRequest;
import com.gesstock.gesstock.approvisionnements.service.EntreeStockService;
import com.gesstock.gesstock.approvisionnements.service.FournisseurCatalogueService;
import com.gesstock.gesstock.caisse.dto.JournalSessionCaisse;
import com.gesstock.gesstock.caisse.dto.MouvementCaisseRequest;
import com.gesstock.gesstock.caisse.dto.MouvementCaisseResponse;
import com.gesstock.gesstock.caisse.dto.TypeEcritureCaisse;
import com.gesstock.gesstock.caisse.service.JournalCaisseService;
import com.gesstock.gesstock.caisse.service.MouvementCaisseService;
import com.gesstock.gesstock.common.exception.ConflitRessourceException;
import com.gesstock.gesstock.entities.StatutDette;
import com.gesstock.gesstock.fournisseurs.dto.DetteFournisseurResponse;
import com.gesstock.gesstock.fournisseurs.service.CompteFournisseurService;
import com.gesstock.gesstock.referentiels.dto.OuvertureSessionRequest;
import com.gesstock.gesstock.referentiels.dto.SessionCaisseResponse;
import com.gesstock.gesstock.referentiels.service.SessionCaisseService;
import com.gesstock.gesstock.repositories.ArticleRepository;
import com.gesstock.gesstock.statistiques.dto.BilanDepensesResponse;
import com.gesstock.gesstock.statistiques.service.StatistiquesService;
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
 * Demande du client (novembre 2026) : la caisse a des entrées de fonds, un bon réceptionné peut sortir aussitôt de la caisse, et
 * les statistiques séparent le chiffre d'affaires, les achats et les autres dépenses. Données de démonstration : ALIM-001 (riz).
 */
@SpringBootTest
@Transactional
class EntreesDeFondsEtDepensesTests {

    private static final String ADMIN = "admin";

    @Autowired
    private SessionCaisseService sessionService;
    @Autowired
    private MouvementCaisseService mouvementService;
    @Autowired
    private JournalCaisseService journalService;
    @Autowired
    private EntreeStockService entreeService;
    @Autowired
    private FournisseurCatalogueService fournisseurService;
    @Autowired
    private CompteFournisseurService compteService;
    @Autowired
    private StatistiquesService statistiquesService;
    @Autowired
    private ArticleRepository articleRepository;

    private SessionCaisseResponse ouvrir(String fond) {
        return sessionService.ouvrir(new OuvertureSessionRequest(LocalDate.now(), new BigDecimal(fond)), ADMIN);
    }

    private BigDecimal solde() {
        return sessionService.sessionOuverteCourante().soldeTheorique();
    }

    private ApprovisionnementResponse bonBrouillon(Long fournisseurId, String numero, String quantite, String prix) {
        Long article = articleRepository.findByReferenceIgnoreCase("ALIM-001").orElseThrow().getId();
        return entreeService.creer(new ApprovisionnementRequest(numero, LocalDate.now(), fournisseurId,
            List.of(new LigneApprovisionnementRequest(article, new BigDecimal(quantite), new BigDecimal(prix)))));
    }

    private BilanDepensesResponse bilan() {
        return statistiquesService.rapport(LocalDate.now(), LocalDate.now()).bilanDepenses();
    }

    @Test
    void uneEntreeDeFondsAugmenteLesEspecesEtPermetUneSortieQueLaCaisseNeCouvraitPas() {
        ouvrir("1000");
        MouvementCaisseRequest sortie = new MouvementCaisseRequest("Achat de glace", "Moussa", new BigDecimal("5000"));
        assertThatThrownBy(() -> mouvementService.enregistrer(sortie, ADMIN)).isInstanceOf(ConflitRessourceException.class);

        MouvementCaisseResponse entree = mouvementService.enregistrerEntree(
            new MouvementCaisseRequest("Apport du patron", "Patron", new BigDecimal("20000")), ADMIN);

        assertThat(entree.type()).isEqualTo("ENTREE");
        assertThat(entree.origine()).isEqualTo("MANUEL");
        assertThat(solde()).isEqualByComparingTo("21000");
        mouvementService.enregistrer(sortie, ADMIN);
        assertThat(solde()).isEqualByComparingTo("16000");
        SessionCaisseResponse session = sessionService.sessionOuverteCourante();
        assertThat(session.entreesDeFonds()).isEqualByComparingTo("20000");
        assertThat(session.sortiesCaisse()).isEqualByComparingTo("5000");
    }

    @Test
    void uneEntreeDeFondsExigeUneCaisseOuverte() {
        assertThatThrownBy(() -> mouvementService.enregistrerEntree(
            new MouvementCaisseRequest("Apport", "Patron", BigDecimal.TEN), ADMIN))
            .isInstanceOf(ConflitRessourceException.class)
            .hasMessageContaining("session de caisse ouverte");
    }

    @Test
    void leJournalDeCaisseMontreLEntreeDeFondsEtSonSoldeSuitLeSoldeTheorique() {
        ouvrir("1000");
        mouvementService.enregistrerEntree(new MouvementCaisseRequest("Apport du patron", "Patron", new BigDecimal("20000")), ADMIN);
        mouvementService.enregistrer(new MouvementCaisseRequest("Transport", "Moussa", new BigDecimal("2500")), ADMIN);

        JournalSessionCaisse journee = journalService.journal(LocalDate.now(), LocalDate.now()).sessions().get(0);

        assertThat(journee.entreesDeFonds()).isEqualByComparingTo("20000");
        assertThat(journee.sorties()).isEqualByComparingTo("2500");
        assertThat(journee.ecritures()).extracting(ecriture -> ecriture.type())
            .contains(TypeEcritureCaisse.ENTREE_FONDS, TypeEcritureCaisse.SORTIE);
        assertThat(journee.soldeTheorique()).isEqualByComparingTo("18500").isEqualByComparingTo(solde());
    }

    @Test
    void reglerLeBonEnEspecesALaReceptionFaitSortirLArgentEtSoldeLaDette() {
        ouvrir("1000");
        mouvementService.enregistrerEntree(new MouvementCaisseRequest("Apport du patron", "Patron", new BigDecimal("20000")), ADMIN);
        FournisseurResponse f = fournisseurService.creer(new FournisseurRequest("Grossiste comptant", null, null, null));
        ApprovisionnementResponse brouillon = bonBrouillon(f.id(), "BR-CASH-1", "10", "1000");

        entreeService.valider(brouillon.id(), ADMIN, true);

        assertThat(solde()).as("21 000 − 10 000").isEqualByComparingTo("11000");
        DetteFournisseurResponse dette = compteService.obtenir(f.id()).dettes().get(0);
        assertThat(dette.statut()).isEqualTo(StatutDette.REGLEE);
        assertThat(dette.solde()).isEqualByComparingTo("0");
        assertThat(mouvementService.lister(sessionService.sessionOuverteCourante().id()))
            .anySatisfy(mouvement -> {
                assertThat(mouvement.type()).isEqualTo("SORTIE");
                assertThat(mouvement.origine()).isEqualTo("REGLEMENT_FOURNISSEUR");
                assertThat(mouvement.montant()).isEqualByComparingTo("10000");
            });
    }

    @Test
    void sansPayerEnEspecesLaReceptionLaisseLaDetteEtNeToucheAucuneEspece() {
        ouvrir("1000");
        FournisseurResponse f = fournisseurService.creer(new FournisseurRequest("Grossiste à crédit", null, null, null));
        ApprovisionnementResponse brouillon = bonBrouillon(f.id(), "BR-CRED-1", "10", "1000");

        entreeService.valider(brouillon.id(), ADMIN, false);

        assertThat(solde()).isEqualByComparingTo("1000");
        assertThat(compteService.obtenir(f.id()).dettes().get(0).statut()).isEqualTo(StatutDette.EN_COURS);
    }

    @Test
    void sLaCaisseNeCouvrePasLeBonLeMessageDitQuoiFaire() {
        ouvrir("1000");
        FournisseurResponse f = fournisseurService.creer(new FournisseurRequest("Grossiste trop cher", null, null, null));
        ApprovisionnementResponse brouillon = bonBrouillon(f.id(), "BR-CASH-2", "10", "1000");

        assertThatThrownBy(() -> entreeService.valider(brouillon.id(), ADMIN, true))
            .isInstanceOf(ConflitRessourceException.class)
            .hasMessageContaining("Paiement en espèces impossible")
            .hasMessageContaining("entrée de fonds")
            .hasMessageContaining("décochez");
    }

    @Test
    void sansCaisseOuverteOnPeutQuandMemeRecevoirUnBonSansLePayer() {
        FournisseurResponse f = fournisseurService.creer(new FournisseurRequest("Grossiste matinal", null, null, null));
        ApprovisionnementResponse brouillon = bonBrouillon(f.id(), "BR-TOT-1", "2", "1000");

        assertThat(entreeService.valider(brouillon.id(), ADMIN, false).statut().name()).isEqualTo("RECU");
        assertThatThrownBy(() -> entreeService.valider(bonBrouillon(f.id(), "BR-TOT-2", "2", "1000").id(), ADMIN, true))
            .hasMessageContaining("session de caisse ouverte");
    }

    @Test
    void lesStatistiquesSeparentAchatsAutresDepensesEtEntreesDeFonds() {
        BilanDepensesResponse avant = bilan();
        ouvrir("1000");
        mouvementService.enregistrerEntree(new MouvementCaisseRequest("Apport du patron", "Patron", new BigDecimal("30000")), ADMIN);
        mouvementService.enregistrer(new MouvementCaisseRequest("Transport", "Moussa", new BigDecimal("2500")), ADMIN);
        FournisseurResponse f = fournisseurService.creer(new FournisseurRequest("Grossiste stats", null, null, null));
        entreeService.valider(bonBrouillon(f.id(), "BR-STAT-1", "10", "1000").id(), ADMIN, true);   // payé : sortie de 10 000
        entreeService.valider(bonBrouillon(f.id(), "BR-STAT-2", "4", "500").id(), ADMIN, false);    // à crédit : 2 000

        BilanDepensesResponse apres = bilan();

        assertThat(apres.achats().subtract(avant.achats())).as("les deux bons comptent, payés ou non").isEqualByComparingTo("12000");
        assertThat(apres.nombreBonsRecus() - avant.nombreBonsRecus()).isEqualTo(2);
        assertThat(apres.autresDepenses().subtract(avant.autresDepenses()))
            .as("seule la sortie saisie à la main : le paiement du fournisseur est déjà dans les achats").isEqualByComparingTo("2500");
        assertThat(apres.totalDepenses().subtract(avant.totalDepenses())).isEqualByComparingTo("14500");
        assertThat(apres.entreesDeFonds().subtract(avant.entreesDeFonds())).isEqualByComparingTo("30000");
        assertThat(apres.resultat().subtract(avant.resultat()))
            .as("l'entrée de fonds n'est pas du chiffre d'affaires : elle ne change pas le résultat").isEqualByComparingTo("-14500");
        assertThat(apres.bonsRecus()).extracting(achat -> achat.numero()).contains("BR-STAT-1", "BR-STAT-2");
        assertThat(apres.depenses()).extracting(depense -> depense.motif()).contains("Transport");
    }
}
