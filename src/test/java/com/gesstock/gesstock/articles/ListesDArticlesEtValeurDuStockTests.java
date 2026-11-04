package com.gesstock.gesstock.articles;

import com.gesstock.gesstock.articles.service.ArticleExportService;
import com.gesstock.gesstock.articles.service.ArticleExportService.FiltreStock;
import com.gesstock.gesstock.common.exception.RequeteInvalideException;
import com.gesstock.gesstock.entities.Article;
import com.gesstock.gesstock.repositories.ArticleRepository;
import com.gesstock.gesstock.statistiques.dto.DashboardStatistiquesResponse;
import com.gesstock.gesstock.statistiques.service.StatistiquesService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Demande du client (novembre 2026) : afficher et exporter le stock disponible, la valeur du stock à la revente au tableau de
 * bord, et la liste des articles classée par catégorie, avec ou sans le stock. Lecture seule : rien n'est modifié.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ListesDArticlesEtValeurDuStockTests {

    private static final String EXPORT = "/api/articles/export";

    @Autowired
    private ArticleExportService exportService;
    @Autowired
    private ArticleRepository articleRepository;
    @Autowired
    private StatistiquesService statistiquesService;
    @Autowired
    private MockMvc mockMvc;

    @Test
    void laValeurAuPrixDeReventeEstLeStockFoisLePrixDeVente() {
        BigDecimal attendu = articleRepository.findAll().stream()
            .map(article -> article.getPrix().multiply(article.getQuantiteStock()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        DashboardStatistiquesResponse tableauDeBord = statistiquesService.dashboard();

        assertThat(tableauDeBord.valeurStockRevente()).isEqualByComparingTo(attendu);
        assertThat(tableauDeBord.valeurStockRevente()).as("la revente rapporte plus que le coût").isGreaterThanOrEqualTo(articleRepository.valeurTotaleStock());
        assertThat(tableauDeBord.nombreArticlesDisponibles())
            .isEqualTo(articleRepository.findAll().stream().filter(article -> article.getQuantiteStock().signum() > 0).count());
    }

    @Test
    void lesFiltresDeStockRetiennentLesMemesArticlesQueL_ecran() {
        var disponibles = exportService.regrouperPourTest(FiltreStock.DISPONIBLE, null);
        var enRupture = exportService.regrouperPourTest(FiltreStock.RUPTURE, null);
        var bas = exportService.regrouperPourTest(FiltreStock.BAS, null);

        assertThat(disponibles.values().stream().flatMap(java.util.List::stream))
            .isNotEmpty().allSatisfy(article -> assertThat(article.getQuantiteStock().signum()).isPositive());
        assertThat(enRupture.values().stream().flatMap(java.util.List::stream))
            .allSatisfy(article -> assertThat(article.getQuantiteStock().signum()).isLessThanOrEqualTo(0));
        assertThat(bas.values().stream().flatMap(java.util.List::stream)).allSatisfy(article -> {
            assertThat(article.getQuantiteStock().signum()).isPositive();
            assertThat(article.getQuantiteStock()).isLessThanOrEqualTo(BigDecimal.valueOf(article.getSeuilAlerte()));
        });
    }

    @Test
    void lesArticlesSontClassesParCategorieSansCategorieEnDernierEtParNom() {
        var groupes = exportService.regrouperPourTest(FiltreStock.TOUS, null);

        assertThat(groupes.keySet()).isNotEmpty();
        var noms = new java.util.ArrayList<>(groupes.keySet());
        var sansSansCategorie = noms.stream().filter(nom -> !"Sans catégorie".equals(nom)).toList();
        assertThat(sansSansCategorie).isSortedAccordingTo(String.CASE_INSENSITIVE_ORDER);
        if (noms.contains("Sans catégorie")) assertThat(noms.get(noms.size() - 1)).isEqualTo("Sans catégorie");
        groupes.values().forEach(liste -> assertThat(liste.stream().map(Article::getNom).toList())
            .isSortedAccordingTo(String.CASE_INSENSITIVE_ORDER));
    }

    @Test
    void unFiltreInconnuOuUneCategorieInconnueSontRefuses() {
        assertThatThrownBy(() -> FiltreStock.de("n'importe quoi")).isInstanceOf(RequeteInvalideException.class);
        assertThat(FiltreStock.de(null)).isEqualTo(FiltreStock.TOUS);
        assertThat(FiltreStock.de("stock-bas")).isEqualTo(FiltreStock.BAS);
        assertThatThrownBy(() -> exportService.exporterPdf(FiltreStock.TOUS, -1L, true)).isInstanceOf(RequeteInvalideException.class);
    }

    @Test
    void lesDeuxFormatsSontDeVraisFichiers() throws Exception {
        assertThat(new String(exportService.exporterPdf(FiltreStock.DISPONIBLE, null, true), 0, 5)).isEqualTo("%PDF-");
        assertThat(new String(exportService.exporterPdf(FiltreStock.TOUS, null, false), 0, 5)).isEqualTo("%PDF-");
        assertThat(new String(exportService.exporterExcel(FiltreStock.DISPONIBLE, null, true), 0, 2)).isEqualTo("PK");
        assertThat(new String(exportService.exporterExcel(FiltreStock.TOUS, null, false), 0, 2)).isEqualTo("PK");
        assertThat(new String(exportService.exporterPdf(FiltreStock.RUPTURE, null, true), 0, 5)).as("même vide, un vrai document").isEqualTo("%PDF-");
    }

    @Test
    void toutLeMondeLitLesListesMaisSeulementAvecUneConnexion() throws Exception {
        for (String role : new String[] {"ADMIN", "MANAGER", "GESTIONNAIRE", "CAISSIER"}) {
            mockMvc.perform(get(EXPORT + "/pdf").param("stock", "disponible").with(user("u").roles(role)))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=gesstock-articles-disponible.pdf"));
        }
        mockMvc.perform(get(EXPORT + "/excel").param("stock", "bas").param("avecStock", "false").with(user("u").roles("CAISSIER")))
            .andExpect(status().isOk())
            .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .andExpect(header().string("Content-Disposition", "attachment; filename=gesstock-articles-stock-bas-simple.xlsx"));
        mockMvc.perform(get(EXPORT + "/pdf")).andExpect(status().isUnauthorized());
        mockMvc.perform(get(EXPORT + "/pdf").param("stock", "inconnu").with(user("u").roles("ADMIN"))).andExpect(status().isBadRequest());
    }
}
