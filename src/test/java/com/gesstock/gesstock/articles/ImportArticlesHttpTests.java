package com.gesstock.gesstock.articles;

import com.gesstock.gesstock.entities.Article;
import com.gesstock.gesstock.repositories.ArticleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * L'import d'articles par l'API (envoi de fichier, droits par métier). Contexte isolé : le post-processeur csrf() remplace le dépôt
 * de jetons du filtre dans le contexte partagé, ce qui ferait échouer CsrfCookieTests s'il passait après ces essais (c'est arrivé en CI).
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
class ImportArticlesHttpTests {

    private static final String TITRES = "Référence;Désignation;Catégorie;Unité;Prix d'achat;Prix de vente;Stock;Seuil d'alerte";

    @Autowired
    private ArticleRepository articleRepository;
    @Autowired
    private MockMvc mockMvc;

    private static byte[] csv(String... lignes) {
        return String.join("\n", lignes).getBytes(StandardCharsets.UTF_8);
    }

    private Article article(String reference) {
        return articleRepository.findByReferenceIgnoreCase(reference).orElseThrow();
    }

    @Test
    void l_apercuEtLImportPassentParLApiPourLeStockEtJamaisPourLaCaisse() throws Exception {
        var fichier = new MockMultipartFile("fichier", "articles.csv", "text/csv", csv(TITRES, "API-1;Biscuit;;;200;300;10;2"));

        mockMvc.perform(multipart("/api/articles/import/apercu").file(fichier).with(user("g").roles("GESTIONNAIRE")).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nouveaux").value(1))
            .andExpect(jsonPath("$.importable").value(true))
            .andExpect(jsonPath("$.lignes[0].statut").value("NOUVEAU"));
        mockMvc.perform(multipart("/api/articles/import").file(fichier).with(user("g").roles("MANAGER")).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.crees").value(1));
        assertThat(article("API-1").getQuantiteStock()).isEqualByComparingTo("10");

        mockMvc.perform(multipart("/api/articles/import/apercu").file(fichier).with(user("c").roles("CAISSIER")).with(csrf()))
            .andExpect(status().isForbidden());
        mockMvc.perform(multipart("/api/articles/import").file(fichier).with(user("c").roles("CAISSIER")).with(csrf()))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/articles/import/modele").with(user("c").roles("CAISSIER"))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/articles/import/modele").param("avecArticles", "true").with(user("c").roles("CAISSIER")))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/articles/import/modele").with(user("g").roles("GESTIONNAIRE")))
            .andExpect(status().isOk())
            .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        mockMvc.perform(get("/api/articles/import/modele")).andExpect(status().isUnauthorized());
    }

    @Test
    void unTypeDeFichierInconnuEstRefuseAvecUn400() throws Exception {
        var fichier = new MockMultipartFile("fichier", "articles.pdf", "application/pdf", "x".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/articles/import/apercu").file(fichier).with(user("g").roles("GESTIONNAIRE")).with(csrf()))
            .andExpect(status().isBadRequest());
    }
}
