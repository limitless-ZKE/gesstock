package com.gesstock.gesstock.articles;

import com.gesstock.gesstock.articles.dto.ArticleRequest;
import com.gesstock.gesstock.articles.dto.ArticleResponse;
import com.gesstock.gesstock.articles.dto.CategorieRequest;
import com.gesstock.gesstock.articles.dto.CategorieResponse;
import com.gesstock.gesstock.articles.dto.UniteVenteRequest;
import com.gesstock.gesstock.articles.dto.UniteVenteResponse;
import com.gesstock.gesstock.articles.service.ArticleCatalogueService;
import com.gesstock.gesstock.articles.service.CategorieArticleService;
import com.gesstock.gesstock.articles.service.UniteVenteArticleService;
import com.gesstock.gesstock.common.exception.ConflitRessourceException;
import com.gesstock.gesstock.common.exception.RessourceIntrouvableException;
import com.gesstock.gesstock.referentiels.dto.UniteMesureRequest;
import com.gesstock.gesstock.referentiels.service.UniteMesureService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class ArticlesServiceTests {

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private CategorieArticleService categorieService;
    @Autowired
    private ArticleCatalogueService articleService;
    @Autowired
    private UniteVenteArticleService uniteVenteService;
    @Autowired
    private UniteMesureService uniteMesureService;

    private ArticleRequest article(String reference, int stock, int seuil) {
        return new ArticleRequest(reference, "Article " + reference, new BigDecimal("100"),
            new BigDecimal("150"), seuil, BigDecimal.valueOf(stock), null, null);
    }

    // --- Catégories et sous-catégories ---

    @Test
    void sousCategorieEstRattacheeASonParentEtListable() {
        CategorieResponse parent = categorieService.creer(new CategorieRequest("Parent test", null, true, null));
        CategorieResponse enfant = categorieService.creer(new CategorieRequest("Enfant test", null, true, parent.id()));

        assertThat(enfant.parentId()).isEqualTo(parent.id());
        assertThat(categorieService.lister(parent.id())).extracting(CategorieResponse::id).containsExactly(enfant.id());
    }

    @Test
    void categorieNePeutPasEtreSonPropreParentNiCreerUnCycle() {
        CategorieResponse a = categorieService.creer(new CategorieRequest("Cycle A", null, true, null));
        CategorieResponse b = categorieService.creer(new CategorieRequest("Cycle B", null, true, a.id()));

        assertThatThrownBy(() -> categorieService.modifier(a.id(), new CategorieRequest("Cycle A", null, true, a.id())))
            .isInstanceOf(ConflitRessourceException.class);
        assertThatThrownBy(() -> categorieService.modifier(a.id(), new CategorieRequest("Cycle A", null, true, b.id())))
            .isInstanceOf(ConflitRessourceException.class);
    }

    @Test
    void categorieAvecSousCategorieNePeutPasEtreSupprimee() {
        CategorieResponse parent = categorieService.creer(new CategorieRequest("Parent supp", null, true, null));
        categorieService.creer(new CategorieRequest("Enfant supp", null, true, parent.id()));
        entityManager.flush();
        entityManager.clear(); // recharge le parent avec sa liste de sous-catégories

        assertThatThrownBy(() -> categorieService.supprimer(parent.id()))
            .isInstanceOf(ConflitRessourceException.class);
    }

    // --- Articles ---

    @Test
    void articleCrudEtReferenceUnique() {
        ArticleResponse cree = articleService.creer(article("TST-001", 10, 5));
        assertThat(cree.seuilAlerteStock()).isEqualTo(5);

        assertThatThrownBy(() -> articleService.creer(article("tst-001", 1, 1)))
            .isInstanceOf(ConflitRessourceException.class);

        ArticleResponse modifie = articleService.modifier(cree.id(), article("TST-001", 10, 8));
        assertThat(modifie.seuilAlerteStock()).isEqualTo(8);

        articleService.supprimer(cree.id());
        assertThatThrownBy(() -> articleService.obtenir(cree.id()))
            .isInstanceOf(RessourceIntrouvableException.class);
    }

    @Test
    void alerteStockListeSeulementLesArticlesSousLeSeuil() {
        ArticleResponse enAlerte = articleService.creer(article("ALR-001", 2, 5));
        ArticleResponse ok = articleService.creer(article("ALR-002", 50, 5));

        assertThat(articleService.listerEnAlerte()).extracting(ArticleResponse::id)
            .contains(enAlerte.id())
            .doesNotContain(ok.id());
    }

    // --- Unités de vente ---

    @Test
    void uniteDeVenteAssocieeALArticle() {
        ArticleResponse art = articleService.creer(article("UV-001", 10, 1));
        Long uniteMesureId = uniteMesureService.creer(new UniteMesureRequest("CRT", "Carton")).id();

        UniteVenteResponse uv = uniteVenteService.associer(art.id(),
            new UniteVenteRequest("CARTON", uniteMesureId, new BigDecimal("12"), new BigDecimal("1700"), true));
        assertThat(uv.articleId()).isEqualTo(art.id());
        assertThat(uniteVenteService.lister(art.id())).extracting(UniteVenteResponse::id).contains(uv.id());

        uniteVenteService.supprimer(art.id(), uv.id());
        assertThat(uniteVenteService.lister(art.id())).extracting(UniteVenteResponse::id).doesNotContain(uv.id());
    }
}
