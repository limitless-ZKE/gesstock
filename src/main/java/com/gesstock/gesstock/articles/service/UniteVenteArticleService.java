package com.gesstock.gesstock.articles.service;

import com.gesstock.gesstock.articles.dto.UniteVenteRequest;
import com.gesstock.gesstock.articles.dto.UniteVenteResponse;
import com.gesstock.gesstock.common.exception.RessourceIntrouvableException;
import com.gesstock.gesstock.articles.mapper.CatalogueMapper;
import com.gesstock.gesstock.entities.Article;
import com.gesstock.gesstock.entities.UniteMesure;
import com.gesstock.gesstock.entities.UniteVente;
import com.gesstock.gesstock.repositories.ArticleRepository;
import com.gesstock.gesstock.repositories.UniteMesureRepository;
import com.gesstock.gesstock.repositories.UniteVenteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class UniteVenteArticleService {

    private final ArticleRepository articleRepository;
    private final UniteMesureRepository uniteMesureRepository;
    private final UniteVenteRepository uniteVenteRepository;

    public UniteVenteArticleService(
        ArticleRepository articleRepository,
        UniteMesureRepository uniteMesureRepository,
        UniteVenteRepository uniteVenteRepository
    ) {
        this.articleRepository = articleRepository;
        this.uniteMesureRepository = uniteMesureRepository;
        this.uniteVenteRepository = uniteVenteRepository;
    }

    @Transactional(readOnly = true)
    public List<UniteVenteResponse> lister(Long articleId) {
        trouverArticle(articleId);
        return uniteVenteRepository.findByArticleIdOrderByIdAsc(articleId).stream()
            .map(CatalogueMapper::toResponse).toList();
    }

    public UniteVenteResponse associer(Long articleId, UniteVenteRequest request) {
        UniteVente uniteVente = new UniteVente();
        remplir(uniteVente, trouverArticle(articleId), request);
        return CatalogueMapper.toResponse(uniteVenteRepository.save(uniteVente));
    }

    public UniteVenteResponse modifier(Long articleId, Long uniteVenteId, UniteVenteRequest request) {
        UniteVente uniteVente = trouverUniteVente(articleId, uniteVenteId);
        remplir(uniteVente, uniteVente.getArticle(), request);
        return CatalogueMapper.toResponse(uniteVenteRepository.save(uniteVente));
    }

    public void supprimer(Long articleId, Long uniteVenteId) {
        uniteVenteRepository.delete(trouverUniteVente(articleId, uniteVenteId));
    }

    private void remplir(UniteVente uniteVente, Article article, UniteVenteRequest request) {
        UniteMesure uniteMesure = uniteMesureRepository.findById(request.uniteMesureId())
            .orElseThrow(() -> new RessourceIntrouvableException(
                "Unité de mesure introuvable: " + request.uniteMesureId()));
        uniteVente.setArticle(article);
        uniteVente.setUniteMesure(uniteMesure);
        uniteVente.setCode(request.code() == null || request.code().isBlank() ? null : request.code().trim());
        uniteVente.setFacteurConversion(request.facteurConversion());
        uniteVente.setPrixVente(request.prixVente());
        uniteVente.setActive(request.active() == null || request.active());
    }

    private Article trouverArticle(Long id) {
        return articleRepository.findById(id)
            .orElseThrow(() -> new RessourceIntrouvableException("Article introuvable: " + id));
    }

    private UniteVente trouverUniteVente(Long articleId, Long uniteVenteId) {
        return uniteVenteRepository.findById(uniteVenteId)
            .filter(uniteVente -> uniteVente.getArticle().getId().equals(articleId))
            .orElseThrow(() -> new RessourceIntrouvableException(
                "Association d'unité de vente introuvable pour cet article"));
    }
}
