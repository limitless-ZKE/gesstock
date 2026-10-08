package com.gesstock.gesstock.articles.service;

import com.gesstock.gesstock.articles.dto.ArticleRequest;
import com.gesstock.gesstock.articles.dto.ArticleResponse;
import com.gesstock.gesstock.common.exception.ConflitRessourceException;
import com.gesstock.gesstock.common.exception.RequeteInvalideException;
import com.gesstock.gesstock.common.exception.RessourceIntrouvableException;
import com.gesstock.gesstock.articles.mapper.CatalogueMapper;
import com.gesstock.gesstock.entities.Article;
import com.gesstock.gesstock.entities.CategorieArticle;
import com.gesstock.gesstock.entities.UniteMesure;
import com.gesstock.gesstock.repositories.ArticleRepository;
import com.gesstock.gesstock.repositories.CategorieArticleRepository;
import com.gesstock.gesstock.repositories.UniteMesureRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class ArticleCatalogueService {

    private final ArticleRepository articleRepository;
    private final CategorieArticleRepository categorieRepository;
    private final UniteMesureRepository uniteMesureRepository;

    public ArticleCatalogueService(
        ArticleRepository articleRepository,
        CategorieArticleRepository categorieRepository,
        UniteMesureRepository uniteMesureRepository
    ) {
        this.articleRepository = articleRepository;
        this.categorieRepository = categorieRepository;
        this.uniteMesureRepository = uniteMesureRepository;
    }

    @Transactional(readOnly = true)
    public List<ArticleResponse> lister(String recherche) {
        List<Article> articles = recherche == null || recherche.isBlank()
            ? articleRepository.findAll()
            : articleRepository.findByNomContainingIgnoreCase(recherche.trim());
        return articles.stream().map(CatalogueMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ArticleResponse obtenir(Long id) {
        return CatalogueMapper.toResponse(trouver(id));
    }

    @Transactional(readOnly = true)
    public List<ArticleResponse> listerEnAlerte() {
        return articleRepository.findArticlesSousSeuilAlerte().stream().map(CatalogueMapper::toResponse).toList();
    }

    public ArticleResponse creer(ArticleRequest request) {
        verifierReferenceDisponible(request.reference(), null);
        Article article = new Article();
        remplir(article, request, true);
        return CatalogueMapper.toResponse(articleRepository.save(article));
    }

    public ArticleResponse modifier(Long id, ArticleRequest request) {
        Article article = trouver(id);
        verifierReferenceDisponible(request.reference(), id);
        // Le stock ne se corrige JAMAIS ici : toute variation doit laisser un mouvement (ajustement de stock), sinon
        // la somme des mouvements ne correspond plus au stock et l'erreur ou la fraude devient invisible.
        if (request.quantiteStock() != null && request.quantiteStock() != article.getQuantiteStock()) {
            throw new RequeteInvalideException(
                "Le stock ne se modifie pas depuis la fiche article : utilisez « Ajuster le stock » (motif obligatoire)");
        }
        remplir(article, request, false);
        return CatalogueMapper.toResponse(articleRepository.save(article));
    }

    public void supprimer(Long id) {
        articleRepository.delete(trouver(id));
    }

    private void remplir(Article article, ArticleRequest request, boolean creation) {
        article.setReference(request.reference().trim());
        article.setDesignation(request.designation().trim());
        article.setPrixAchat(request.prixAchat());
        article.setPrix(request.prixVente());
        article.setSeuilAlerte(request.seuilAlerteStock());
        if (creation && request.quantiteStock() != null) {
            article.setQuantiteStock(request.quantiteStock()); // stock de départ, à la création uniquement
        }
        if (creation) {
            article.setCmp(request.prixAchat()); // premier coût connu ; ensuite seules les entrées en stock le font évoluer
        }
        article.setCategorie(request.categorieId() == null ? null : trouverCategorie(request.categorieId()));
        article.setUniteMesure(request.uniteMesureId() == null ? null : trouverUnite(request.uniteMesureId()));
    }

    private void verifierReferenceDisponible(String reference, Long idIgnore) {
        articleRepository.findByReferenceIgnoreCase(reference.trim()).ifPresent(existing -> {
            if (!existing.getId().equals(idIgnore)) {
                throw new ConflitRessourceException("Un article portant cette référence existe déjà");
            }
        });
    }

    private CategorieArticle trouverCategorie(Long id) {
        return categorieRepository.findById(id)
            .orElseThrow(() -> new RessourceIntrouvableException("Catégorie introuvable: " + id));
    }

    private UniteMesure trouverUnite(Long id) {
        return uniteMesureRepository.findById(id)
            .orElseThrow(() -> new RessourceIntrouvableException("Unité de mesure introuvable: " + id));
    }

    private Article trouver(Long id) {
        return articleRepository.findById(id)
            .orElseThrow(() -> new RessourceIntrouvableException("Article introuvable: " + id));
    }
}
