package com.gesstock.gesstock.approvisionnements;

import com.gesstock.gesstock.approvisionnements.dto.ApprovisionnementRequest;
import com.gesstock.gesstock.approvisionnements.dto.ApprovisionnementResponse;
import com.gesstock.gesstock.approvisionnements.dto.FournisseurRequest;
import com.gesstock.gesstock.approvisionnements.dto.FournisseurResponse;
import com.gesstock.gesstock.approvisionnements.dto.LigneApprovisionnementRequest;
import com.gesstock.gesstock.approvisionnements.service.EntreeStockService;
import com.gesstock.gesstock.approvisionnements.service.FournisseurCatalogueService;
import com.gesstock.gesstock.common.exception.ConflitRessourceException;
import com.gesstock.gesstock.common.exception.RessourceIntrouvableException;
import com.gesstock.gesstock.entities.Article;
import com.gesstock.gesstock.entities.StatutApprovisionnement;
import com.gesstock.gesstock.repositories.ArticleRepository;
import com.gesstock.gesstock.repositories.MouvementStockRepository;
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

/** Les données de démonstration (admin, article ELEC-001 en stock) viennent du DataSeeder. */
@SpringBootTest
@Transactional
class ApprovisionnementServiceTests {

    private static final String ADMIN = "admin";

    @Autowired
    private FournisseurCatalogueService fournisseurService;
    @Autowired
    private EntreeStockService entreeService;
    @Autowired
    private ArticleRepository articleRepository;
    @Autowired
    private MouvementStockRepository mouvementRepository;
    @Autowired
    private EntityManager entityManager;

    private FournisseurResponse fournisseur(String nom) {
        return fournisseurService.creer(new FournisseurRequest(nom, "0100000000", "contact@example.com", "Adresse"));
    }

    private ApprovisionnementRequest bon(String numero, Long fournisseurId, Long articleId, int quantite, String prix) {
        return new ApprovisionnementRequest(numero, LocalDate.now(), fournisseurId,
            List.of(new LigneApprovisionnementRequest(articleId, BigDecimal.valueOf(quantite), new BigDecimal(prix))));
    }

    private Article telephone() {
        return articleRepository.findByReferenceIgnoreCase("ELEC-001").orElseThrow();
    }

    // --- Fournisseurs ---

    @Test
    void fournisseurCrudEtNomUnique() {
        FournisseurResponse cree = fournisseur("Fournisseur test");
        assertThatThrownBy(() -> fournisseur("fournisseur TEST")).isInstanceOf(ConflitRessourceException.class);

        FournisseurResponse modifie = fournisseurService.modifier(cree.id(),
            new FournisseurRequest("Fournisseur renommé", null, null, null));
        assertThat(modifie.nom()).isEqualTo("Fournisseur renommé");

        fournisseurService.supprimer(cree.id());
        assertThatThrownBy(() -> fournisseurService.obtenir(cree.id()))
            .isInstanceOf(RessourceIntrouvableException.class);
    }

    @Test
    void fournisseurAvecBonNePeutPasEtreSupprime() {
        FournisseurResponse f = fournisseur("Fournisseur lié");
        entreeService.creer(bon("BR-LIE-1", f.id(), telephone().getId(), 1, "100"));
        entityManager.flush();
        entityManager.clear();

        assertThatThrownBy(() -> fournisseurService.supprimer(f.id()))
            .isInstanceOf(ConflitRessourceException.class);
    }

    // --- Entrées en stock ---

    @Test
    void brouillonNeChangePasLeStockMaisLaValidationLIncremente() {
        Article article = telephone();
        BigDecimal stockInitial = article.getQuantiteStock();
        FournisseurResponse f = fournisseur("Fournisseur stock");

        ApprovisionnementResponse brouillon = entreeService.creer(bon("BR-001", f.id(), article.getId(), 7, "200000"));
        assertThat(brouillon.statut()).isEqualTo(StatutApprovisionnement.BROUILLON);
        assertThat(brouillon.montantTotal()).isEqualByComparingTo("1400000");
        assertThat(telephone().getQuantiteStock()).isEqualByComparingTo(stockInitial);

        long mouvementsAvant = mouvementRepository.count();
        ApprovisionnementResponse recu = entreeService.valider(brouillon.id(), ADMIN);

        assertThat(recu.statut()).isEqualTo(StatutApprovisionnement.RECU);
        Article apres = telephone();
        assertThat(apres.getQuantiteStock()).isEqualByComparingTo(stockInitial.add(BigDecimal.valueOf(7)));
        assertThat(apres.getPrixAchat()).isEqualByComparingTo("200000");
        assertThat(mouvementRepository.count()).isEqualTo(mouvementsAvant + 1);
    }

    @Test
    void bonRecuNePeutPlusEtreValideModifieNiSupprime() {
        FournisseurResponse f = fournisseur("Fournisseur figé");
        ApprovisionnementResponse bon = entreeService.creer(bon("BR-002", f.id(), telephone().getId(), 1, "100"));
        entreeService.valider(bon.id(), ADMIN);

        assertThatThrownBy(() -> entreeService.valider(bon.id(), ADMIN)).isInstanceOf(ConflitRessourceException.class);
        assertThatThrownBy(() -> entreeService.supprimer(bon.id())).isInstanceOf(ConflitRessourceException.class);
        assertThatThrownBy(() -> entreeService.modifier(bon.id(),
            bon("BR-002", f.id(), telephone().getId(), 2, "100"))).isInstanceOf(ConflitRessourceException.class);
    }

    @Test
    void numeroDeBonUniqueEtArticleOuFournisseurInconnuRefuses() {
        FournisseurResponse f = fournisseur("Fournisseur num");
        entreeService.creer(bon("BR-003", f.id(), telephone().getId(), 1, "100"));

        assertThatThrownBy(() -> entreeService.creer(bon("br-003", f.id(), telephone().getId(), 1, "100")))
            .isInstanceOf(ConflitRessourceException.class);
        assertThatThrownBy(() -> entreeService.creer(bon("BR-004", f.id(), -1L, 1, "100")))
            .isInstanceOf(RessourceIntrouvableException.class);
        assertThatThrownBy(() -> entreeService.creer(bon("BR-005", -1L, telephone().getId(), 1, "100")))
            .isInstanceOf(RessourceIntrouvableException.class);
    }
}
