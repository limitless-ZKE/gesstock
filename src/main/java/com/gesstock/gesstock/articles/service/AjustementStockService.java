package com.gesstock.gesstock.articles.service;

import com.gesstock.gesstock.articles.dto.AjustementStockRequest;
import com.gesstock.gesstock.articles.dto.ArticleResponse;
import com.gesstock.gesstock.articles.dto.MotifAjustementStock;
import com.gesstock.gesstock.articles.mapper.CatalogueMapper;
import com.gesstock.gesstock.common.exception.ConflitRessourceException;
import com.gesstock.gesstock.common.exception.RequeteInvalideException;
import com.gesstock.gesstock.common.exception.RessourceIntrouvableException;
import com.gesstock.gesstock.entities.Article;
import com.gesstock.gesstock.entities.MouvementStock;
import com.gesstock.gesstock.entities.TypeMouvementStock;
import com.gesstock.gesstock.journal.service.JournalActiviteService;
import com.gesstock.gesstock.repositories.ArticleRepository;
import com.gesstock.gesstock.repositories.MouvementStockRepository;
import com.gesstock.gesstock.repositories.UtilisateurRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Seul moyen de corriger le stock hors réception et vente : chaque ajustement laisse un mouvement (avant, après,
 * motif, auteur) et une ligne de journal, de sorte que la somme des mouvements explique toujours le stock.
 */
@Service
@Transactional
public class AjustementStockService {

    public static final String REFERENCE = "AJUSTEMENT";

    private final ArticleRepository articleRepository;
    private final MouvementStockRepository mouvementStockRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final JournalActiviteService journalActiviteService;

    public AjustementStockService(
        ArticleRepository articleRepository,
        MouvementStockRepository mouvementStockRepository,
        UtilisateurRepository utilisateurRepository,
        JournalActiviteService journalActiviteService
    ) {
        this.articleRepository = articleRepository;
        this.mouvementStockRepository = mouvementStockRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.journalActiviteService = journalActiviteService;
    }

    public ArticleResponse ajuster(Long articleId, AjustementStockRequest request, String username) {
        boolean commentaireAbsent = request.commentaire() == null || request.commentaire().isBlank();
        if (request.motif() == MotifAjustementStock.AUTRE && commentaireAbsent) {
            throw new RequeteInvalideException("Précisez la raison de l'ajustement dans le commentaire (motif « Autre »)");
        }

        // verrou pessimiste : une vente qui s'exécute en même temps attend la fin de l'ajustement
        Article article = articleRepository.findByIdForUpdate(articleId)
            .orElseThrow(() -> new RessourceIntrouvableException("Article introuvable: " + articleId));
        int stockAvant = article.getQuantiteStock();
        if (stockAvant != request.stockAffiche()) {
            throw new ConflitRessourceException("Le stock a changé depuis l'affichage (" + stockAvant
                + " actuellement) : rechargez la fiche puis recommencez");
        }
        int stockApres = request.nouveauStock();
        if (stockApres == stockAvant) {
            throw new ConflitRessourceException("Le stock de cet article est déjà de " + stockAvant);
        }

        String details = request.motif().libelle() + (commentaireAbsent ? "" : " — " + request.commentaire().trim());
        MouvementStock mouvement = new MouvementStock();
        mouvement.setType(stockApres > stockAvant
            ? TypeMouvementStock.AJUSTEMENT_POSITIF : TypeMouvementStock.AJUSTEMENT_NEGATIF);
        mouvement.setQuantite(Math.abs(stockApres - stockAvant));
        mouvement.setStockAvant(stockAvant);
        mouvement.setStockApres(stockApres);
        mouvement.setDateMouvement(LocalDateTime.now());
        mouvement.setReferenceDocument(REFERENCE);
        mouvement.setCommentaire(details);
        mouvement.setArticle(article);
        utilisateurRepository.findByUsername(username).ifPresent(mouvement::setEffectuePar);
        mouvementStockRepository.save(mouvement);

        article.setQuantiteStock(stockApres);
        Article enregistre = articleRepository.save(article);
        journalActiviteService.enregistrer("AJUSTEMENT_STOCK", "ARTICLE", enregistre.getId(),
            "Stock de " + enregistre.getReference() + " : " + stockAvant + " → " + stockApres + " (" + details + ")",
            username);
        return CatalogueMapper.toResponse(enregistre);
    }
}
