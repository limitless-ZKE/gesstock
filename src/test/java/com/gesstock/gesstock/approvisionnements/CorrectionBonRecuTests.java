package com.gesstock.gesstock.approvisionnements;

import com.gesstock.gesstock.approvisionnements.dto.ApprovisionnementRequest;
import com.gesstock.gesstock.approvisionnements.dto.ApprovisionnementResponse;
import com.gesstock.gesstock.approvisionnements.dto.FournisseurRequest;
import com.gesstock.gesstock.approvisionnements.dto.FournisseurResponse;
import com.gesstock.gesstock.approvisionnements.dto.LigneApprovisionnementRequest;
import com.gesstock.gesstock.approvisionnements.service.EntreeStockService;
import com.gesstock.gesstock.approvisionnements.service.FournisseurCatalogueService;
import com.gesstock.gesstock.common.exception.ConflitRessourceException;
import com.gesstock.gesstock.entities.Article;
import com.gesstock.gesstock.entities.ModeReglement;
import com.gesstock.gesstock.entities.StatutApprovisionnement;
import com.gesstock.gesstock.entities.StatutDette;
import com.gesstock.gesstock.fournisseurs.dto.DetteFournisseurResponse;
import com.gesstock.gesstock.fournisseurs.dto.LigneRetourFournisseurRequest;
import com.gesstock.gesstock.fournisseurs.dto.ReglementFournisseurRequest;
import com.gesstock.gesstock.fournisseurs.dto.RetourFournisseurRequest;
import com.gesstock.gesstock.fournisseurs.service.CompteFournisseurService;
import com.gesstock.gesstock.fournisseurs.service.RetourFournisseurService;
import com.gesstock.gesstock.repositories.ArticleRepository;
import com.gesstock.gesstock.repositories.JournalActiviteRepository;
import jakarta.persistence.EntityManager;
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
 * Demande du client (novembre 2026) : un bon d'approvisionnement se modifie aussi APRÈS sa réception ; le stock, le coût
 * moyen, les prix et la dette envers le fournisseur suivent. Le prix de revente saisi sur le bon s'applique à la réception.
 * Données de démonstration : ALIM-001 (riz) coûte 900, se vend 1 300.
 */
@SpringBootTest
@Transactional
class CorrectionBonRecuTests {

    private static final String ADMIN = "admin";

    @Autowired
    private EntreeStockService entreeService;
    @Autowired
    private FournisseurCatalogueService fournisseurService;
    @Autowired
    private CompteFournisseurService compteService;
    @Autowired
    private RetourFournisseurService retourService;
    @Autowired
    private ArticleRepository articleRepository;
    @Autowired
    private JournalActiviteRepository journalRepository;
    @Autowired
    private EntityManager entityManager;

    private Article riz() {
        return articleRepository.findByReferenceIgnoreCase("ALIM-001").orElseThrow();
    }

    private FournisseurResponse fournisseur(String nom) {
        return fournisseurService.creer(new FournisseurRequest(nom, null, null, null));
    }

    private ApprovisionnementRequest bon(Long fournisseurId, String quantite, String prixAchat, String prixVente) {
        return new ApprovisionnementRequest("BR-CORR-1", LocalDate.now(), fournisseurId, List.of(new LigneApprovisionnementRequest(
            riz().getId(), new BigDecimal(quantite), new BigDecimal(prixAchat), prixVente == null ? null : new BigDecimal(prixVente))));
    }

    private ApprovisionnementResponse recevoir(Long fournisseurId, String quantite, String prixAchat) {
        ApprovisionnementResponse brouillon = entreeService.creer(bon(fournisseurId, quantite, prixAchat, null));
        return entreeService.valider(brouillon.id(), ADMIN);
    }

    private DetteFournisseurResponse dette(Long fournisseurId) {
        return compteService.obtenir(fournisseurId).dettes().get(0);
    }

    private void relire() {
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void lePrixDeReventeSaisiSurLeBonDevientLePrixDeVenteALaReception() {
        FournisseurResponse f = fournisseur("Grossiste revente");
        ApprovisionnementResponse brouillon = entreeService.creer(bon(f.id(), "10", "950", "1400"));
        assertThat(brouillon.lignes().get(0).prixVente()).isEqualByComparingTo("1400");
        assertThat(riz().getPrix()).as("un brouillon ne change rien à la caisse").isEqualByComparingTo("1300");

        entreeService.valider(brouillon.id(), ADMIN);
        relire();

        assertThat(riz().getPrix()).isEqualByComparingTo("1400");
        assertThat(riz().getPrixAchat()).isEqualByComparingTo("950");
        assertThat(journalRepository.findAll()).anySatisfy(entree ->
            assertThat(entree.getDetails()).contains("ALIM-001").contains("prix de vente 1300 → 1400").contains("BR-CORR-1"));
    }

    @Test
    void corrigerLaQuantiteEtLePrixDUnBonRecuCorrigeLeStockLeCoutEtLaDette() {
        FournisseurResponse f = fournisseur("Grossiste correction");
        BigDecimal stockAvant = riz().getQuantiteStock();
        ApprovisionnementResponse recu = recevoir(f.id(), "10", "1000");
        assertThat(dette(f.id()).montantInitial()).isEqualByComparingTo("10000");

        // le livreur avait en fait apporté 8 sacs, facturés 1 100
        ApprovisionnementResponse corrige = entreeService.modifier(recu.id(), bon(f.id(), "8", "1100", null), ADMIN);
        relire();

        assertThat(corrige.statut()).isEqualTo(StatutApprovisionnement.RECU);
        assertThat(riz().getQuantiteStock()).isEqualByComparingTo(stockAvant.add(new BigDecimal("8")));
        assertThat(riz().getPrixAchat()).isEqualByComparingTo("1100");
        assertThat(dette(f.id()).montantInitial()).isEqualByComparingTo("8800");
        // le coût moyen est celui qu'aurait donné la bonne réception d'emblée : (stock d'avant × 900 + 8 × 1 100) / total
        BigDecimal attendu = stockAvant.multiply(new BigDecimal("900")).add(new BigDecimal("8800"))
            .divide(stockAvant.add(new BigDecimal("8")), 4, java.math.RoundingMode.HALF_UP);
        assertThat(riz().getCmp()).isCloseTo(attendu, org.assertj.core.data.Offset.offset(new BigDecimal("0.01")));
        assertThat(journalRepository.findAll()).anySatisfy(entree -> {
            assertThat(entree.getAction()).isEqualTo("MODIFICATION");
            assertThat(entree.getEntite()).isEqualTo("APPROVISIONNEMENT");
            assertThat(entree.getDetails()).contains("total 10000 → 8800").contains("ALIM-001 10 à 1000 → 8 à 1100");
        });
    }

    @Test
    void onNeRetireJamaisDuStockCeQuiADejaEteVendu() {
        FournisseurResponse f = fournisseur("Grossiste vendu");
        ApprovisionnementResponse recu = recevoir(f.id(), "10", "1000");
        riz().setQuantiteStock(new BigDecimal("4"));   // tout le reste a été vendu depuis
        relire();

        assertThatThrownBy(() -> entreeService.modifier(recu.id(), bon(f.id(), "5", "1000", null), ADMIN))
            .isInstanceOf(ConflitRessourceException.class)
            .hasMessageContaining("il n'en reste que 4 en stock")
            .hasMessageContaining("ne peut pas descendre sous 6");
    }

    @Test
    void laDetteNeDescendPasSousCeQuiADejaEtePayeAuFournisseur() {
        FournisseurResponse f = fournisseur("Grossiste payé");
        ApprovisionnementResponse recu = recevoir(f.id(), "10", "1000");
        compteService.payer(dette(f.id()).id(), new ReglementFournisseurRequest(new BigDecimal("9000"), ModeReglement.VIREMENT, "VIR-1"), ADMIN);

        assertThatThrownBy(() -> entreeService.modifier(recu.id(), bon(f.id(), "8", "1000", null), ADMIN))
            .isInstanceOf(ConflitRessourceException.class)
            .hasMessageContaining("a déjà reçu 9000");

        entreeService.modifier(recu.id(), bon(f.id(), "9.5", "1000", null), ADMIN);
        relire();
        DetteFournisseurResponse corrigee = dette(f.id());
        assertThat(corrigee.montantInitial()).isEqualByComparingTo("9500");
        assertThat(corrigee.statut()).isEqualTo(StatutDette.PARTIELLEMENT_REGLEE);
    }

    @Test
    void unBonQuiAFaitLObjetDUnRetourNeSeCorrigePlus() {
        FournisseurResponse f = fournisseur("Grossiste retour");
        ApprovisionnementResponse recu = recevoir(f.id(), "10", "1000");
        Long ligne = retourService.lignesRetournables(recu.id()).get(0).ligneApprovisionnementId();
        retourService.creer(new RetourFournisseurRequest(recu.id(), "Sacs percés",
            List.of(new LigneRetourFournisseurRequest(ligne, BigDecimal.ONE))), ADMIN);

        assertThatThrownBy(() -> entreeService.modifier(recu.id(), bon(f.id(), "9", "1000", null), ADMIN))
            .isInstanceOf(ConflitRessourceException.class)
            .hasMessageContaining("retour au fournisseur");
    }
}
