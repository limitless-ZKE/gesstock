package com.gesstock.gesstock.articles.mapper;

import com.gesstock.gesstock.articles.dto.ArticleResponse;
import com.gesstock.gesstock.articles.dto.CategorieResponse;
import com.gesstock.gesstock.articles.dto.UniteVenteResponse;
import com.gesstock.gesstock.entities.Article;
import com.gesstock.gesstock.entities.CategorieArticle;
import com.gesstock.gesstock.entities.UniteVente;

public final class CatalogueMapper {

    private CatalogueMapper() {}

    public static CategorieResponse toResponse(CategorieArticle categorie) {
        return new CategorieResponse(
            categorie.getId(),
            categorie.getNom(),
            categorie.getDescription(),
            categorie.isActive(),
            categorie.getParent() == null ? null : categorie.getParent().getId()
        );
    }

    public static ArticleResponse toResponse(Article article) {
        return new ArticleResponse(
            article.getId(),
            article.getReference(),
            article.getDesignation(),
            article.getPrixAchat(),
            article.getCmp(),
            article.getPrix(),
            article.getQuantiteStock(),
            article.getSeuilAlerte(),
            article.getCategorie() == null ? null : article.getCategorie().getId(),
            article.getCategorie() == null ? null : article.getCategorie().getNom(),
            article.getUniteMesure() == null ? null : article.getUniteMesure().getId(),
            article.getUniteMesure() == null ? null : article.getUniteMesure().getLibelle()
        );
    }

    public static UniteVenteResponse toResponse(UniteVente uniteVente) {
        return new UniteVenteResponse(
            uniteVente.getId(),
            uniteVente.getArticle().getId(),
            uniteVente.getCode(),
            uniteVente.getUniteMesure().getId(),
            uniteVente.getUniteMesure().getCode(),
            uniteVente.getUniteMesure().getLibelle(),
            uniteVente.getFacteurConversion(),
            uniteVente.getPrixVente(),
            uniteVente.isActive()
        );
    }
}
