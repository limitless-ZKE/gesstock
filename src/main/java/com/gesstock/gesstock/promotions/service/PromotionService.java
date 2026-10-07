package com.gesstock.gesstock.promotions.service;

import com.gesstock.gesstock.entities.Article;
import com.gesstock.gesstock.entities.Promotion;
import com.gesstock.gesstock.entities.TypePromotion;
import com.gesstock.gesstock.promotions.dto.PromotionRequest;
import com.gesstock.gesstock.promotions.dto.PromotionResponse;
import com.gesstock.gesstock.promotions.exception.PromotionIntrouvableException;
import com.gesstock.gesstock.promotions.exception.PromotionInvalideException;
import com.gesstock.gesstock.repositories.ArticleRepository;
import com.gesstock.gesstock.repositories.PromotionRepository;
import com.gesstock.gesstock.journal.service.JournalActiviteService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
public class PromotionService {

    private final PromotionRepository promotionRepository;
    private final ArticleRepository articleRepository;
    private final JournalActiviteService journalActiviteService;

    public PromotionService(
        PromotionRepository promotionRepository,
        ArticleRepository articleRepository,
        JournalActiviteService journalActiviteService
    ) {
        this.promotionRepository = promotionRepository;
        this.articleRepository = articleRepository;
        this.journalActiviteService = journalActiviteService;
    }

    @Transactional(readOnly = true)
    public List<PromotionResponse> lister() {
        return promotionRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public PromotionResponse obtenir(Long id) {
        return toResponse(trouver(id));
    }

    public PromotionResponse creer(PromotionRequest request) {
        validerPeriode(request);
        Promotion promotion = new Promotion();
        remplir(promotion, request);
        Promotion saved = promotionRepository.save(promotion);
        tracer("CREATION", saved);
        return toResponse(saved);
    }

    public PromotionResponse modifier(Long id, PromotionRequest request) {
        validerPeriode(request);
        Promotion promotion = trouver(id);
        remplir(promotion, request);
        Promotion saved = promotionRepository.save(promotion);
        tracer("MODIFICATION", saved);
        return toResponse(saved);
    }

    public void supprimer(Long id) {
        Promotion promotion = trouver(id);
        tracer("SUPPRESSION", promotion);
        promotionRepository.delete(promotion);
    }

    @Transactional(readOnly = true)
    public Promotion promotionApplicable(Article article, LocalDateTime instant) {
        return promotionRepository.findPromotionsApplicables(
                article.getId(),
                article.getCategorie() == null ? null : article.getCategorie().getId(),
                instant)
            .stream()
            .filter(promotion -> promotion.getType() == TypePromotion.POURCENTAGE)
            .max((first, second) -> first.getValeur().compareTo(second.getValeur()))
            .orElse(null);
    }

    @Transactional(readOnly = true)
    public Promotion promotionChoisie(Article article, Long promotionId, LocalDateTime instant) {
        if (promotionId == null) {
            return promotionApplicable(article, instant);
        }
        Promotion promotion = promotionRepository.findById(promotionId)
            .orElseThrow(() -> new PromotionIntrouvableException("Promotion introuvable: " + promotionId));
        boolean cibleArticle = promotion.getArticles().stream()
            .anyMatch(cible -> cible.getId().equals(article.getId()));
        boolean cibleCategorie = article.getCategorie() != null && promotion.getCategories().stream()
            .anyMatch(categorie -> categorie.getId().equals(article.getCategorie().getId()));
        if (!promotion.isActive() || instant.isBefore(promotion.getDebut()) || instant.isAfter(promotion.getFin())
            || (!cibleArticle && !cibleCategorie) || promotion.getType() != TypePromotion.POURCENTAGE) {
            throw new PromotionInvalideException("Cette promotion n'est pas applicable à l'article ou à la période");
        }
        return promotion;
    }

    public BigDecimal montantRemise(Promotion promotion, BigDecimal prix) {
        if (promotion == null) {
            return BigDecimal.ZERO;
        }
        return prix.multiply(promotion.getValeur())
            .divide(BigDecimal.valueOf(100), 4, java.math.RoundingMode.HALF_UP);
    }

    private void remplir(Promotion promotion, PromotionRequest request) {
        List<Article> articles = articleRepository.findAllById(request.articleIds());
        if (articles.size() != request.articleIds().size()) {
            Set<Long> idsTrouves = articles.stream().map(Article::getId).collect(Collectors.toSet());
            Set<Long> inconnus = new HashSet<>(request.articleIds());
            inconnus.removeAll(idsTrouves);
            throw new PromotionIntrouvableException("Articles introuvables: " + inconnus);
        }
        promotion.setNom(request.nom().trim());
        promotion.setType(TypePromotion.POURCENTAGE);
        promotion.setValeur(request.pourcentage());
        promotion.setDebut(request.debut());
        promotion.setFin(request.fin());
        promotion.setActive(request.active() == null || request.active());
        promotion.setArticles(articles);
        promotion.setCategories(List.of());
    }

    private void validerPeriode(PromotionRequest request) {
        if (request.fin().isBefore(request.debut())) {
            throw new PromotionInvalideException("La fin de promotion doit être après son début");
        }
    }

    private Promotion trouver(Long id) {
        return promotionRepository.findById(id)
            .orElseThrow(() -> new PromotionIntrouvableException("Promotion introuvable: " + id));
    }

    private PromotionResponse toResponse(Promotion promotion) {
        return new PromotionResponse(
            promotion.getId(),
            promotion.getNom(),
            promotion.getValeur(),
            promotion.getDebut(),
            promotion.getFin(),
            promotion.isActive(),
            promotion.getArticles().stream().map(Article::getId).collect(Collectors.toSet())
        );
    }

    private void tracer(String action, Promotion promotion) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication == null ? null : authentication.getName();
        journalActiviteService.enregistrer(action, "PROMOTION", promotion.getId(),
            "Promotion " + promotion.getNom() + " (" + promotion.getValeur() + "%)", username);
    }
}
