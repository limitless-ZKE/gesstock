package com.gesstock.gesstock.fournisseurs;

import com.gesstock.gesstock.approvisionnements.dto.ApprovisionnementRequest;
import com.gesstock.gesstock.approvisionnements.dto.ApprovisionnementResponse;
import com.gesstock.gesstock.approvisionnements.dto.FournisseurRequest;
import com.gesstock.gesstock.approvisionnements.dto.FournisseurResponse;
import com.gesstock.gesstock.approvisionnements.dto.LigneApprovisionnementRequest;
import com.gesstock.gesstock.approvisionnements.service.EntreeStockService;
import com.gesstock.gesstock.approvisionnements.service.FournisseurCatalogueService;
import com.gesstock.gesstock.caisse.service.MouvementCaisseService;
import com.gesstock.gesstock.common.exception.ConflitRessourceException;
import com.gesstock.gesstock.common.exception.RessourceIntrouvableException;
import com.gesstock.gesstock.entities.MouvementCaisse;
import com.gesstock.gesstock.entities.ModeReglement;
import com.gesstock.gesstock.entities.SessionCaisse;
import com.gesstock.gesstock.entities.SourceReglementFournisseur;
import com.gesstock.gesstock.entities.StatutDette;
import com.gesstock.gesstock.entities.StatutSessionCaisse;
import com.gesstock.gesstock.fournisseurs.dto.AvoirFournisseurRequest;
import com.gesstock.gesstock.fournisseurs.dto.AvoirFournisseurResponse;
import com.gesstock.gesstock.fournisseurs.dto.CompteFournisseurResponse;
import com.gesstock.gesstock.fournisseurs.dto.CompteFournisseurResume;
import com.gesstock.gesstock.fournisseurs.dto.DetteFournisseurResponse;
import com.gesstock.gesstock.fournisseurs.dto.ReglementFournisseurRequest;
import com.gesstock.gesstock.fournisseurs.dto.ReglementFournisseurResponse;
import com.gesstock.gesstock.fournisseurs.service.CompteFournisseurService;
import com.gesstock.gesstock.referentiels.dto.OuvertureSessionRequest;
import com.gesstock.gesstock.referentiels.service.SessionCaisseService;
import com.gesstock.gesstock.repositories.ArticleRepository;
import com.gesstock.gesstock.repositories.MouvementCaisseRepository;
import com.gesstock.gesstock.repositories.SessionCaisseRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Compte fournisseur : une dette par bon reçu, paiements, avoirs imputés d'eux-mêmes sur les dettes les plus anciennes. */
@SpringBootTest
@Transactional
class CompteFournisseurTests {

    private static final String ADMIN = "admin";

    @Autowired
    private FournisseurCatalogueService fournisseurService;
    @Autowired
    private EntreeStockService entreeService;
    @Autowired
    private CompteFournisseurService compteService;
    @Autowired
    private ArticleRepository articleRepository;
    @Autowired
    private SessionCaisseService sessionService;
    @Autowired
    private SessionCaisseRepository sessionRepository;
    @Autowired
    private MouvementCaisseService mouvementCaisseService;
    @Autowired
    private MouvementCaisseRepository mouvementCaisseRepository;

    private FournisseurResponse fournisseur(String nom) {
        return fournisseurService.creer(new FournisseurRequest(nom, "0100000000", null, null));
    }

    /** Crée et reçoit un bon d'un seul article ; la dette vaut quantité × prix. */
    private ApprovisionnementResponse bonRecu(String numero, FournisseurResponse fournisseur, int quantite, String prix) {
        Long articleId = articleRepository.findByReferenceIgnoreCase("ELEC-001").orElseThrow().getId();
        ApprovisionnementResponse brouillon = entreeService.creer(new ApprovisionnementRequest(numero, LocalDate.now(),
            fournisseur.id(), List.of(new LigneApprovisionnementRequest(articleId, BigDecimal.valueOf(quantite), new BigDecimal(prix)))));
        return entreeService.valider(brouillon.id(), ADMIN);
    }

    private CompteFournisseurResponse compte(FournisseurResponse fournisseur) {
        return compteService.obtenir(fournisseur.id());
    }

    private DetteFournisseurResponse dette(FournisseurResponse fournisseur, String numeroBon) {
        return compte(fournisseur).dettes().stream().filter(d -> d.numeroBon().equals(numeroBon)).findFirst().orElseThrow();
    }

    private ReglementFournisseurRequest paiement(String montant, ModeReglement mode) {
        return new ReglementFournisseurRequest(new BigDecimal(montant), mode, null);
    }

    private SessionCaisse ouvrirCaisse(String fond) {
        sessionService.ouvrir(new OuvertureSessionRequest(null, new BigDecimal(fond)), ADMIN);
        return sessionRepository.findByJourneeAndStatut(LocalDate.now(), StatutSessionCaisse.OUVERTE).orElseThrow();
    }

    @Test
    void recevoirUnBonCreeLaDetteDuFournisseur() {
        FournisseurResponse f = fournisseur("Grossiste A");
        assertThat(compte(f).dettes()).isEmpty();

        bonRecu("BR-CPT-1", f, 3, "10000");

        CompteFournisseurResponse compte = compte(f);
        assertThat(compte.soldeDu()).isEqualByComparingTo("30000");
        assertThat(compte.creditDisponible()).isEqualByComparingTo("0");
        assertThat(compte.dettes()).singleElement().satisfies(d -> {
            assertThat(d.numeroBon()).isEqualTo("BR-CPT-1");
            assertThat(d.montantInitial()).isEqualByComparingTo("30000");
            assertThat(d.montantRegle()).isEqualByComparingTo("0");
            assertThat(d.statut()).isEqualTo(StatutDette.EN_COURS);
        });
    }

    @Test
    void unBrouillonNeCreeAucuneDetteEtUnBonSansValeurNonPlus() {
        FournisseurResponse f = fournisseur("Grossiste B");
        Long articleId = articleRepository.findByReferenceIgnoreCase("ELEC-001").orElseThrow().getId();
        entreeService.creer(new ApprovisionnementRequest("BR-CPT-2", LocalDate.now(), f.id(),
            List.of(new LigneApprovisionnementRequest(articleId, BigDecimal.valueOf(1), new BigDecimal("500")))));
        assertThat(compte(f).dettes()).as("un brouillon n'est pas une dette").isEmpty();

        bonRecu("BR-CPT-3", f, 2, "0");   // échantillons gratuits
        assertThat(compte(f).dettes()).as("rien à payer").isEmpty();
    }

    @Test
    void lesPaiementsSoldentLaDetteParEtapesEtNeDepassentJamaisLeSolde() {
        FournisseurResponse f = fournisseur("Grossiste C");
        bonRecu("BR-CPT-4", f, 1, "100000");
        Long detteId = dette(f, "BR-CPT-4").id();

        ReglementFournisseurResponse premier = compteService.payer(detteId, paiement("40000", ModeReglement.VIREMENT), ADMIN);
        assertThat(premier.source()).isEqualTo(SourceReglementFournisseur.PAIEMENT);
        assertThat(premier.effectuePar()).isEqualTo(ADMIN);
        assertThat(dette(f, "BR-CPT-4").statut()).isEqualTo(StatutDette.PARTIELLEMENT_REGLEE);
        assertThat(compte(f).soldeDu()).isEqualByComparingTo("60000");

        assertThatThrownBy(() -> compteService.payer(detteId, paiement("60000.0001", ModeReglement.VIREMENT), ADMIN))
            .isInstanceOf(ConflitRessourceException.class).hasMessageContaining("60000");

        compteService.payer(detteId, paiement("60000", ModeReglement.CHEQUE), ADMIN);
        DetteFournisseurResponse soldee = dette(f, "BR-CPT-4");
        assertThat(soldee.statut()).isEqualTo(StatutDette.REGLEE);
        assertThat(soldee.solde()).isEqualByComparingTo("0");
        assertThat(soldee.reglements()).hasSize(2);
        assertThat(compte(f).soldeDu()).isEqualByComparingTo("0");

        assertThatThrownBy(() -> compteService.payer(detteId, paiement("1", ModeReglement.VIREMENT), ADMIN))
            .isInstanceOf(ConflitRessourceException.class).hasMessageContaining("déjà réglée");
        assertThatThrownBy(() -> compteService.payer(-1L, paiement("1", ModeReglement.VIREMENT), ADMIN))
            .isInstanceOf(RessourceIntrouvableException.class);
    }

    @Test
    void unPaiementEnEspecesSortDeLaCaisseEtExigeUneSessionOuverteAvecAssezDeLiquide() {
        FournisseurResponse f = fournisseur("Grossiste D");
        bonRecu("BR-CPT-5", f, 1, "100000");
        Long detteId = dette(f, "BR-CPT-5").id();

        assertThatThrownBy(() -> compteService.payer(detteId, paiement("1000", ModeReglement.ESPECES), ADMIN))
            .as("pas de session ouverte").isInstanceOf(ConflitRessourceException.class);
        assertThat(dette(f, "BR-CPT-5").montantRegle()).as("rien n'est enregistré si la caisse refuse").isEqualByComparingTo("0");

        SessionCaisse session = ouvrirCaisse("50000");
        assertThatThrownBy(() -> compteService.payer(detteId, paiement("60000", ModeReglement.ESPECES), ADMIN))
            .as("plus que le liquide en caisse").isInstanceOf(ConflitRessourceException.class);

        compteService.payer(detteId, paiement("30000", ModeReglement.ESPECES), ADMIN);

        List<MouvementCaisse> sorties = mouvementCaisseRepository.findBySessionCaisseIdOrderByDateMouvementDesc(session.getId());
        assertThat(sorties).singleElement().satisfies(sortie -> {
            assertThat(sortie.getMontant()).isEqualByComparingTo("30000");
            assertThat(sortie.getBeneficiaire()).isEqualTo("Grossiste D");
            assertThat(sortie.getMotif()).contains("BR-CPT-5");
        });
        assertThat(mouvementCaisseService.soldeEspecesDisponible(session)).isEqualByComparingTo("20000");
        assertThat(dette(f, "BR-CPT-5").montantRegle()).isEqualByComparingTo("30000");
    }

    @Test
    void unPaiementParVirementNeToucheJamaisALaCaisse() {
        FournisseurResponse f = fournisseur("Grossiste E");
        bonRecu("BR-CPT-6", f, 1, "10000");
        long sortiesAvant = mouvementCaisseRepository.count();

        compteService.payer(dette(f, "BR-CPT-6").id(), paiement("10000", ModeReglement.VIREMENT), ADMIN);

        assertThat(mouvementCaisseRepository.count()).isEqualTo(sortiesAvant);
        assertThat(dette(f, "BR-CPT-6").statut()).isEqualTo(StatutDette.REGLEE);
    }

    @Test
    void unAvoirSImputeSurLesDettesLesPlusAnciennesDAbord() {
        FournisseurResponse f = fournisseur("Grossiste F");
        bonRecu("BR-CPT-7", f, 1, "100000");
        bonRecu("BR-CPT-8", f, 1, "50000");

        AvoirFournisseurResponse avoir = compteService.creerAvoir(f.id(),
            new AvoirFournisseurRequest(new BigDecimal("120000"), "Ristourne de fin d'année"), ADMIN);

        assertThat(avoir.montantUtilise()).isEqualByComparingTo("120000");
        assertThat(avoir.restant()).isEqualByComparingTo("0");
        assertThat(dette(f, "BR-CPT-7").statut()).as("la plus ancienne est soldée en premier").isEqualTo(StatutDette.REGLEE);
        DetteFournisseurResponse recente = dette(f, "BR-CPT-8");
        assertThat(recente.statut()).isEqualTo(StatutDette.PARTIELLEMENT_REGLEE);
        assertThat(recente.solde()).isEqualByComparingTo("30000");
        assertThat(recente.reglements()).singleElement().satisfies(r -> {
            assertThat(r.source()).isEqualTo(SourceReglementFournisseur.AVOIR);
            assertThat(r.modeReglement()).as("un avoir n'a pas de mode de règlement").isNull();
            assertThat(r.avoirId()).isEqualTo(avoir.id());
            assertThat(r.montant()).isEqualByComparingTo("20000");
        });
        CompteFournisseurResponse compte = compte(f);
        assertThat(compte.soldeDu()).isEqualByComparingTo("30000");
        assertThat(compte.creditDisponible()).isEqualByComparingTo("0");
    }

    @Test
    void leCreditNonConsommeResteDisponiblePuisSImputeSurLeProchainBon() {
        FournisseurResponse f = fournisseur("Grossiste G");
        bonRecu("BR-CPT-9", f, 1, "30000");

        compteService.creerAvoir(f.id(), new AvoirFournisseurRequest(new BigDecimal("100000"), "Retour de marchandise"), ADMIN);

        CompteFournisseurResponse apresAvoir = compte(f);
        assertThat(dette(f, "BR-CPT-9").statut()).isEqualTo(StatutDette.REGLEE);
        assertThat(apresAvoir.soldeDu()).isEqualByComparingTo("0");
        assertThat(apresAvoir.creditDisponible()).as("100 000 - 30 000").isEqualByComparingTo("70000");

        bonRecu("BR-CPT-10", f, 1, "50000");

        CompteFournisseurResponse apresBon = compte(f);
        assertThat(dette(f, "BR-CPT-10").statut()).as("le crédit solde le nouveau bon dès sa réception").isEqualTo(StatutDette.REGLEE);
        assertThat(apresBon.soldeDu()).isEqualByComparingTo("0");
        assertThat(apresBon.creditDisponible()).isEqualByComparingTo("20000");

        bonRecu("BR-CPT-11", f, 1, "50000");

        CompteFournisseurResponse apres = compte(f);
        assertThat(dette(f, "BR-CPT-11").solde()).as("il ne restait que 20 000 de crédit").isEqualByComparingTo("30000");
        assertThat(apres.creditDisponible()).isEqualByComparingTo("0");
        assertThat(apres.soldeDu()).isEqualByComparingTo("30000");
    }

    @Test
    void laListeDesComptesDonneLeSoldeEtLeCreditDeChaqueFournisseur() {
        FournisseurResponse endette = fournisseur("Grossiste H");
        FournisseurResponse crediteur = fournisseur("Grossiste I");
        FournisseurResponse neutre = fournisseur("Grossiste J");
        bonRecu("BR-CPT-12", endette, 2, "5000");
        compteService.creerAvoir(crediteur.id(), new AvoirFournisseurRequest(new BigDecimal("8000"), "Geste commercial"), ADMIN);

        List<CompteFournisseurResume> comptes = compteService.lister();

        assertThat(comptes).filteredOn(c -> c.fournisseurId().equals(endette.id())).singleElement().satisfies(c -> {
            assertThat(c.soldeDu()).isEqualByComparingTo("10000");
            assertThat(c.creditDisponible()).isEqualByComparingTo("0");
        });
        assertThat(comptes).filteredOn(c -> c.fournisseurId().equals(crediteur.id())).singleElement().satisfies(c -> {
            assertThat(c.soldeDu()).isEqualByComparingTo("0");
            assertThat(c.creditDisponible()).isEqualByComparingTo("8000");
        });
        assertThat(comptes).filteredOn(c -> c.fournisseurId().equals(neutre.id())).singleElement().satisfies(c -> {
            assertThat(c.soldeDu()).isEqualByComparingTo("0");
            assertThat(c.creditDisponible()).isEqualByComparingTo("0");
        });
    }

    @Test
    void unFournisseurAvecDesAvoirsNePeutPasEtreSupprime() {
        FournisseurResponse f = fournisseur("Grossiste K");
        compteService.creerAvoir(f.id(), new AvoirFournisseurRequest(new BigDecimal("1000"), "Geste commercial"), ADMIN);

        assertThatThrownBy(() -> fournisseurService.supprimer(f.id())).isInstanceOf(ConflitRessourceException.class);
    }

    @Test
    void fournisseurInconnuEtUtilisateurInconnuSontRefuses() {
        assertThatThrownBy(() -> compteService.obtenir(-1L)).isInstanceOf(RessourceIntrouvableException.class);
        assertThatThrownBy(() -> compteService.creerAvoir(-1L,
            new AvoirFournisseurRequest(new BigDecimal("1"), "x"), ADMIN)).isInstanceOf(RessourceIntrouvableException.class);
        FournisseurResponse f = fournisseur("Grossiste L");
        assertThatThrownBy(() -> compteService.creerAvoir(f.id(),
            new AvoirFournisseurRequest(new BigDecimal("1"), "x"), "fantome")).isInstanceOf(RessourceIntrouvableException.class);
    }
}
