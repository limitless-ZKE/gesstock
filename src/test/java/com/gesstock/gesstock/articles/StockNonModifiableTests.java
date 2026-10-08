package com.gesstock.gesstock.articles;

import com.gesstock.gesstock.articles.dto.ArticleRequest;
import com.gesstock.gesstock.articles.service.ArticleCatalogueService;
import com.gesstock.gesstock.common.exception.RequeteInvalideException;
import com.gesstock.gesstock.entities.Article;
import com.gesstock.gesstock.repositories.ArticleRepository;
import com.gesstock.gesstock.repositories.MouvementStockRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Le stock ne bouge que par un mouvement tracé : jamais en éditant la fiche d'un article. */
@SpringBootTest
@Transactional
class StockNonModifiableTests {

    @Autowired
    private ArticleCatalogueService articleService;
    @Autowired
    private ArticleRepository articleRepository;
    @Autowired
    private MouvementStockRepository mouvementRepository;
    @Autowired
    private EntityManager entityManager;

    private ArticleRequest requete(Article article, Integer stock, String prixVente) {
        return new ArticleRequest(article.getReference(), "Téléphone", article.getPrixAchat(), new BigDecimal(prixVente),
            article.getSeuilAlerte(), stock, null, null);
    }

    @Test
    void modifierLeStockDepuisLaFicheEstRefuseEtLeStockResteIntact() {
        Article telephone = articleRepository.findByReferenceIgnoreCase("ELEC-001").orElseThrow();
        int stock = telephone.getQuantiteStock();
        long mouvements = mouvementRepository.count();

        assertThatThrownBy(() -> articleService.modifier(telephone.getId(), requete(telephone, 999, "245000")))
            .isInstanceOf(RequeteInvalideException.class)
            .hasMessageContaining("Ajuster le stock");

        entityManager.flush();
        entityManager.clear();
        assertThat(articleRepository.findById(telephone.getId()).orElseThrow().getQuantiteStock()).isEqualTo(stock);
        assertThat(mouvementRepository.count()).isEqualTo(mouvements);
    }

    @Test
    void lAncienneValeurDuStockRenvoyeeParLeFormulaireNeBloquePasLaModificationDuPrix() {
        Article telephone = articleRepository.findByReferenceIgnoreCase("ELEC-001").orElseThrow();

        // l'écran renvoie le stock tel qu'il l'a lu : seule la modification du prix doit compter
        var reponse = articleService.modifier(telephone.getId(), requete(telephone, telephone.getQuantiteStock(), "250000"));

        assertThat(reponse.prixVente()).isEqualByComparingTo("250000");
        assertThat(reponse.quantiteStock()).isEqualTo(telephone.getQuantiteStock());
    }

    @Test
    void unNouvelArticleGardeSonStockDeDepart() {
        var cree = articleService.creer(new ArticleRequest("NEUF-001", "Article neuf", new BigDecimal("100"),
            new BigDecimal("150"), 2, 40, null, null));

        assertThat(cree.quantiteStock()).isEqualTo(40);
    }
}
