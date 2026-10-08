package com.gesstock.gesstock.fournisseurs;

import com.gesstock.gesstock.approvisionnements.dto.ApprovisionnementRequest;
import com.gesstock.gesstock.approvisionnements.dto.ApprovisionnementResponse;
import com.gesstock.gesstock.approvisionnements.dto.FournisseurRequest;
import com.gesstock.gesstock.approvisionnements.dto.FournisseurResponse;
import com.gesstock.gesstock.approvisionnements.dto.LigneApprovisionnementRequest;
import com.gesstock.gesstock.approvisionnements.service.EntreeStockService;
import com.gesstock.gesstock.approvisionnements.service.FournisseurCatalogueService;
import com.gesstock.gesstock.common.exception.ConflitRessourceException;
import com.gesstock.gesstock.common.exception.RequeteInvalideException;
import com.gesstock.gesstock.common.exception.RessourceIntrouvableException;
import com.gesstock.gesstock.entities.Article;
import com.gesstock.gesstock.entities.MouvementStock;
import com.gesstock.gesstock.entities.StatutDette;
import com.gesstock.gesstock.entities.StatutRetourFournisseur;
import com.gesstock.gesstock.entities.TypeMouvementStock;
import com.gesstock.gesstock.fournisseurs.dto.CompteFournisseurResponse;
import com.gesstock.gesstock.fournisseurs.dto.DetteFournisseurResponse;
import com.gesstock.gesstock.fournisseurs.dto.LigneRetourFournisseurRequest;
import com.gesstock.gesstock.fournisseurs.dto.LigneRetournableResponse;
import com.gesstock.gesstock.fournisseurs.dto.RetourFournisseurRequest;
import com.gesstock.gesstock.fournisseurs.dto.RetourFournisseurResponse;
import com.gesstock.gesstock.fournisseurs.service.CompteFournisseurService;
import com.gesstock.gesstock.fournisseurs.service.RetourFournisseurService;
import com.gesstock.gesstock.repositories.ArticleRepository;
import com.gesstock.gesstock.repositories.MouvementStockRepository;
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
 * Retours au fournisseur. Données de démonstration : ELEC-001 (stock 12, coût moyen 189 000). Préparé par « gestionnaire »,
 * validé par « admin ».
 */
@SpringBootTest
@Transactional
class RetourFournisseurTests {

    private static final String PREPARATEUR = "gestionnaire";
    private static final String VALIDATEUR = "admin";

    @Autowired
    private FournisseurCatalogueService fournisseurService;
    @Autowired
    private EntreeStockService entreeService;
    @Autowired
    private CompteFournisseurService compteService;
    @Autowired
    private RetourFournisseurService retourService;
    @Autowired
    private ArticleRepository articleRepository;
    @Autowired
    private MouvementStockRepository mouvementRepository;

    private FournisseurResponse fournisseur(String nom) {
        return fournisseurService.creer(new FournisseurRequest(nom, null, null, null));
    }

    private Article telephone() {
        return articleRepository.findByReferenceIgnoreCase("ELEC-001").orElseThrow();
    }

    private ApprovisionnementResponse bonRecu(String numero, FournisseurResponse fournisseur, LigneApprovisionnementRequest... lignes) {
        ApprovisionnementResponse brouillon = entreeService.creer(
            new ApprovisionnementRequest(numero, LocalDate.now(), fournisseur.id(), List.of(lignes)));
        return entreeService.valider(brouillon.id(), VALIDATEUR);
    }

    private LigneApprovisionnementRequest ligne(int quantite, String prix) {
        return new LigneApprovisionnementRequest(telephone().getId(), quantite, new BigDecimal(prix));
    }

    private RetourFournisseurRequest retour(ApprovisionnementResponse bon, String motif, LigneRetourFournisseurRequest... lignes) {
        return new RetourFournisseurRequest(bon.id(), motif, List.of(lignes));
    }

    private LigneRetourFournisseurRequest rendre(LigneRetournableResponse ligne, int quantite) {
        return new LigneRetourFournisseurRequest(ligne.ligneApprovisionnementId(), quantite);
    }

    private DetteFournisseurResponse dette(FournisseurResponse fournisseur) {
        return compteService.obtenir(fournisseur.id()).dettes().get(0);
    }

    @Test
    void unRetourValideSortLeStockAjusteLeCoutMoyenEtCreditLeCompteDuFournisseur() {
        FournisseurResponse f = fournisseur("Grossiste retour");
        ApprovisionnementResponse bon = bonRecu("BR-RET-1", f, ligne(10, "200000"));   // stock 22, coût moyen 194 000
        LigneRetournableResponse retournable = retourService.lignesRetournables(bon.id()).get(0);
        assertThat(retournable.quantiteRetournable()).isEqualTo(10);
        assertThat(retournable.stockActuel()).isEqualTo(22);

        RetourFournisseurResponse brouillon = retourService.creer(retour(bon, "Écrans défectueux", rendre(retournable, 4)), PREPARATEUR);

        assertThat(brouillon.statut()).isEqualTo(StatutRetourFournisseur.BROUILLON);
        assertThat(brouillon.numero()).startsWith("RF-");
        assertThat(brouillon.creePar()).isEqualTo(PREPARATEUR);
        assertThat(brouillon.montantTotal()).isEqualByComparingTo("800000");
        assertThat(telephone().getQuantiteStock()).as("un brouillon ne touche pas au stock").isEqualTo(22);
        assertThat(dette(f).montantRegle()).as("ni au compte").isEqualByComparingTo("0");

        RetourFournisseurResponse valide = retourService.valider(brouillon.id(), VALIDATEUR);

        assertThat(valide.statut()).isEqualTo(StatutRetourFournisseur.VALIDE);
        assertThat(valide.validePar()).isEqualTo(VALIDATEUR);
        assertThat(valide.avoirId()).isNotNull();
        Article apres = telephone();
        assertThat(apres.getQuantiteStock()).isEqualTo(18);
        assertThat(apres.getCmp()).as("(194 000 × 22 − 4 × 200 000) / 18").isEqualByComparingTo("192666.6667");

        List<MouvementStock> mouvements = mouvementRepository.findAll().stream()
            .filter(m -> m.getRetourFournisseur() != null && m.getRetourFournisseur().getId().equals(valide.id())).toList();
        assertThat(mouvements).singleElement().satisfies(m -> {
            assertThat(m.getType()).isEqualTo(TypeMouvementStock.RETOUR_FOURNISSEUR);
            assertThat(m.getQuantite()).isEqualTo(4);
            assertThat(m.getStockAvant()).isEqualTo(22);
            assertThat(m.getStockApres()).isEqualTo(18);
            assertThat(m.getCoutUnitaire()).isEqualByComparingTo("200000");
            assertThat(m.getReferenceDocument()).isEqualTo(valide.numero());
            assertThat(m.getEffectuePar().getUsername()).isEqualTo(VALIDATEUR);
        });

        CompteFournisseurResponse compte = compteService.obtenir(f.id());
        assertThat(compte.avoirs()).singleElement().satisfies(avoir -> {
            assertThat(avoir.montant()).isEqualByComparingTo("800000");
            assertThat(avoir.motif()).contains(valide.numero()).contains("BR-RET-1");
        });
        DetteFournisseurResponse dette = compte.dettes().get(0);
        assertThat(dette.montantRegle()).as("l'avoir s'impute sur la dette du bon").isEqualByComparingTo("800000");
        assertThat(dette.statut()).isEqualTo(StatutDette.PARTIELLEMENT_REGLEE);
        assertThat(compte.soldeDu()).isEqualByComparingTo("1200000");

        LigneRetournableResponse apresRetour = retourService.lignesRetournables(bon.id()).get(0);
        assertThat(apresRetour.quantiteDejaRetournee()).isEqualTo(4);
        assertThat(apresRetour.quantiteRetournable()).isEqualTo(6);
    }

    @Test
    void onNeRendJamaisPlusQueCeQuiEstRecuMemeAvecDeuxRetoursEnBrouillon() {
        FournisseurResponse f = fournisseur("Grossiste plafond");
        ApprovisionnementResponse bon = bonRecu("BR-RET-2", f, ligne(10, "1000"));
        LigneRetournableResponse retournable = retourService.lignesRetournables(bon.id()).get(0);

        assertThatThrownBy(() -> retourService.creer(retour(bon, "Trop", rendre(retournable, 11)), PREPARATEUR))
            .isInstanceOf(ConflitRessourceException.class).hasMessageContaining("10 unité(s) encore retournable(s)");

        RetourFournisseurResponse premier = retourService.creer(retour(bon, "Premier", rendre(retournable, 7)), PREPARATEUR);
        RetourFournisseurResponse second = retourService.creer(retour(bon, "Second", rendre(retournable, 7)), PREPARATEUR);
        retourService.valider(premier.id(), VALIDATEUR);
        int stockApresPremier = telephone().getQuantiteStock();

        assertThatThrownBy(() -> retourService.valider(second.id(), VALIDATEUR))
            .as("les 7 unités restantes ne suffisent pas : la base refuse de compter plus de 10 rendues")
            .isInstanceOf(ConflitRessourceException.class).hasMessageContaining("déjà été rendues");
        assertThat(telephone().getQuantiteStock()).isEqualTo(stockApresPremier);
        assertThat(retourService.obtenir(second.id()).statut()).isEqualTo(StatutRetourFournisseur.BROUILLON);
        assertThat(retourService.lignesRetournables(bon.id()).get(0).quantiteRetournable()).isEqualTo(3);
    }

    @Test
    void onNeRendPasCeQuiAEteVenduDepuis() {
        FournisseurResponse f = fournisseur("Grossiste vendu");
        ApprovisionnementResponse bon = bonRecu("BR-RET-3", f, ligne(10, "1000"));
        LigneRetournableResponse retournable = retourService.lignesRetournables(bon.id()).get(0);
        RetourFournisseurResponse brouillon = retourService.creer(retour(bon, "Retour", rendre(retournable, 4)), PREPARATEUR);

        Article article = telephone();
        article.setQuantiteStock(3);   // le reste a été vendu
        articleRepository.saveAndFlush(article);

        assertThatThrownBy(() -> retourService.valider(brouillon.id(), VALIDATEUR))
            .isInstanceOf(ConflitRessourceException.class)
            .hasMessageContaining("Stock insuffisant").hasMessageContaining("il n'en reste que 3");
        assertThat(telephone().getQuantiteStock()).isEqualTo(3);
        assertThat(retourService.obtenir(brouillon.id()).statut()).isEqualTo(StatutRetourFournisseur.BROUILLON);
        assertThat(compteService.obtenir(f.id()).avoirs()).isEmpty();
    }

    @Test
    void unRetourPeutPorterSurPlusieursLignesDuMemeArticleAuCoutDeChacune() {
        FournisseurResponse f = fournisseur("Grossiste lots");
        ApprovisionnementResponse bon = bonRecu("BR-RET-4", f, ligne(2, "100"), ligne(3, "300"));
        List<LigneRetournableResponse> lignes = retourService.lignesRetournables(bon.id());
        assertThat(lignes).hasSize(2);

        RetourFournisseurResponse brouillon = retourService.creer(
            retour(bon, "Lots périmés", rendre(lignes.get(0), 2), rendre(lignes.get(1), 3)), PREPARATEUR);
        assertThat(brouillon.montantTotal()).as("2 × 100 + 3 × 300").isEqualByComparingTo("1100");

        retourService.valider(brouillon.id(), VALIDATEUR);

        assertThat(telephone().getQuantiteStock()).isEqualTo(12);   // 12 + 5 reçus − 5 rendus
        assertThat(compteService.obtenir(f.id()).avoirs()).singleElement()
            .satisfies(avoir -> assertThat(avoir.montant()).isEqualByComparingTo("1100"));
    }

    @Test
    void unBrouillonSAnnuleMaisPasUnRetourValide() {
        FournisseurResponse f = fournisseur("Grossiste annulation");
        ApprovisionnementResponse bon = bonRecu("BR-RET-5", f, ligne(5, "1000"));
        LigneRetournableResponse retournable = retourService.lignesRetournables(bon.id()).get(0);

        RetourFournisseurResponse abandonne = retourService.creer(retour(bon, "Finalement non", rendre(retournable, 1)), PREPARATEUR);
        assertThat(retourService.annuler(abandonne.id(), PREPARATEUR).statut()).isEqualTo(StatutRetourFournisseur.ANNULE);
        assertThatThrownBy(() -> retourService.valider(abandonne.id(), VALIDATEUR)).isInstanceOf(ConflitRessourceException.class);
        assertThat(retourService.lignesRetournables(bon.id()).get(0).quantiteRetournable()).isEqualTo(5);

        RetourFournisseurResponse valide = retourService.valider(
            retourService.creer(retour(bon, "Défaut", rendre(retournable, 1)), PREPARATEUR).id(), VALIDATEUR);
        assertThatThrownBy(() -> retourService.annuler(valide.id(), VALIDATEUR)).isInstanceOf(ConflitRessourceException.class);
        assertThatThrownBy(() -> retourService.valider(valide.id(), VALIDATEUR)).isInstanceOf(ConflitRessourceException.class);
    }

    @Test
    void seulUnBonRecuPeutFaireLObjetDUnRetourEtLesLignesDoiventLuiAppartenir() {
        FournisseurResponse f = fournisseur("Grossiste règles");
        ApprovisionnementResponse brouillonDeBon = entreeService.creer(
            new ApprovisionnementRequest("BR-RET-6", LocalDate.now(), f.id(), List.of(ligne(1, "100"))));
        assertThatThrownBy(() -> retourService.lignesRetournables(brouillonDeBon.id())).isInstanceOf(ConflitRessourceException.class);

        ApprovisionnementResponse bon = bonRecu("BR-RET-7", f, ligne(5, "100"));
        LigneRetournableResponse retournable = retourService.lignesRetournables(bon.id()).get(0);
        assertThatThrownBy(() -> retourService.creer(
            new RetourFournisseurRequest(brouillonDeBon.id(), "x", List.of(rendre(retournable, 1))), PREPARATEUR))
            .isInstanceOf(ConflitRessourceException.class);
        assertThatThrownBy(() -> retourService.creer(
            retour(bon, "x", new LigneRetourFournisseurRequest(-1L, 1)), PREPARATEUR)).isInstanceOf(RequeteInvalideException.class);
        assertThatThrownBy(() -> retourService.creer(
            retour(bon, "x", rendre(retournable, 1), rendre(retournable, 2)), PREPARATEUR)).isInstanceOf(RequeteInvalideException.class);
        assertThatThrownBy(() -> retourService.creer(
            new RetourFournisseurRequest(-1L, "x", List.of(rendre(retournable, 1))), PREPARATEUR))
            .isInstanceOf(RessourceIntrouvableException.class);
        assertThatThrownBy(() -> retourService.obtenir(-1L)).isInstanceOf(RessourceIntrouvableException.class);
    }

    @Test
    void unBonSansValeurSePeutRendreMaisNeCreeAucunAvoir() {
        FournisseurResponse f = fournisseur("Grossiste gratuit");
        ApprovisionnementResponse bon = bonRecu("BR-RET-8", f, ligne(3, "0"));
        LigneRetournableResponse retournable = retourService.lignesRetournables(bon.id()).get(0);

        RetourFournisseurResponse valide = retourService.valider(
            retourService.creer(retour(bon, "Échantillons rendus", rendre(retournable, 3)), PREPARATEUR).id(), VALIDATEUR);

        assertThat(valide.statut()).isEqualTo(StatutRetourFournisseur.VALIDE);
        assertThat(valide.avoirId()).isNull();
        assertThat(telephone().getQuantiteStock()).isEqualTo(12);
        assertThat(compteService.obtenir(f.id()).avoirs()).isEmpty();
    }
}
