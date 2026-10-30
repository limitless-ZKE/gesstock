package com.gesstock.gesstock.articles.service;

import com.gesstock.gesstock.articles.dto.UniteVenteRequest;
import com.gesstock.gesstock.articles.dto.UniteVenteResponse;
import com.gesstock.gesstock.common.exception.RessourceIntrouvableException;
import com.gesstock.gesstock.articles.mapper.CatalogueMapper;
import com.gesstock.gesstock.entities.Article;
import com.gesstock.gesstock.entities.UniteMesure;
import com.gesstock.gesstock.entities.UniteVente;
import com.gesstock.gesstock.journal.service.JournalActiviteService;
import com.gesstock.gesstock.repositories.ArticleRepository;
import com.gesstock.gesstock.repositories.UniteMesureRepository;
import com.gesstock.gesstock.repositories.UniteVenteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@Transactional
public class UniteVenteArticleService {

    private final ArticleRepository articleRepository;
    private final UniteMesureRepository uniteMesureRepository;
    private final UniteVenteRepository uniteVenteRepository;
    private final JournalActiviteService journalService;

    public UniteVenteArticleService(
        ArticleRepository articleRepository,
        UniteMesureRepository uniteMesureRepository,
        UniteVenteRepository uniteVenteRepository,
        JournalActiviteService journalService
    ) {
        this.articleRepository = articleRepository;
        this.uniteMesureRepository = uniteMesureRepository;
        this.uniteVenteRepository = uniteVenteRepository;
        this.journalService = journalService;
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
        UniteVente enregistree = uniteVenteRepository.save(uniteVente);
        journalService.enregistrer("CREATION", "UNITE_VENTE", enregistree.getId(),
            "Article " + enregistree.getArticle().getReference() + " : unité de vente " + decrire(enregistree) + " ajoutée", null);
        return CatalogueMapper.toResponse(enregistree);
    }

    public UniteVenteResponse modifier(Long articleId, Long uniteVenteId, UniteVenteRequest request) {
        UniteVente uniteVente = trouverUniteVente(articleId, uniteVenteId);
        String avant = decrire(uniteVente);
        remplir(uniteVente, uniteVente.getArticle(), request);
        UniteVente enregistree = uniteVenteRepository.save(uniteVente);
        String apres = decrire(enregistree);
        if (!avant.equals(apres)) {
            journalService.enregistrer("MODIFICATION", "UNITE_VENTE", uniteVenteId,
                "Article " + enregistree.getArticle().getReference() + " : unité de vente " + avant + " → " + apres, null);
        }
        return CatalogueMapper.toResponse(enregistree);
    }

    public void supprimer(Long articleId, Long uniteVenteId) {
        UniteVente uniteVente = trouverUniteVente(articleId, uniteVenteId);
        uniteVenteRepository.delete(uniteVente);
        journalService.enregistrer("SUPPRESSION", "UNITE_VENTE", uniteVenteId,
            "Article " + uniteVente.getArticle().getReference() + " : unité de vente " + decrire(uniteVente) + " retirée", null);
    }

    /** « Casier (× 12) à 7200 », « désactivée » si elle ne se vend plus : tout ce qui fixe le prix d'une unité de vente. */
    private static String decrire(UniteVente uniteVente) {
        String nom = uniteVente.getCode() != null ? uniteVente.getCode()
            : uniteVente.getUniteMesure() == null ? "?" : uniteVente.getUniteMesure().getLibelle();
        return nom + " (× " + texte(uniteVente.getFacteurConversion()) + ") à " + texte(uniteVente.getPrixVente())
            + (uniteVente.isActive() ? "" : ", désactivée");
    }

    private static String texte(BigDecimal valeur) {
        return valeur == null ? "—" : valeur.stripTrailingZeros().toPlainString();
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
