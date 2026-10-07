package com.gesstock.gesstock.approvisionnements.service;

import com.gesstock.gesstock.approvisionnements.dto.ApprovisionnementRequest;
import com.gesstock.gesstock.approvisionnements.dto.ApprovisionnementResponse;
import com.gesstock.gesstock.approvisionnements.dto.LigneApprovisionnementRequest;
import com.gesstock.gesstock.common.exception.ConflitRessourceException;
import com.gesstock.gesstock.common.exception.RessourceIntrouvableException;
import com.gesstock.gesstock.approvisionnements.mapper.ApprovisionnementMapper;
import com.gesstock.gesstock.entities.*;
import com.gesstock.gesstock.repositories.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.gesstock.gesstock.journal.service.JournalActiviteService;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class EntreeStockService {

    private final ApprovisionnementRepository approvisionnementRepository;
    private final FournisseurRepository fournisseurRepository;
    private final ArticleRepository articleRepository;
    private final MouvementStockRepository mouvementStockRepository;
    private final JournalActiviteService journalActiviteService;

    public EntreeStockService(
        ApprovisionnementRepository approvisionnementRepository,
        FournisseurRepository fournisseurRepository,
        ArticleRepository articleRepository,
        MouvementStockRepository mouvementStockRepository,
        JournalActiviteService journalActiviteService
    ) {
        this.approvisionnementRepository = approvisionnementRepository;
        this.fournisseurRepository = fournisseurRepository;
        this.articleRepository = articleRepository;
        this.mouvementStockRepository = mouvementStockRepository;
        this.journalActiviteService = journalActiviteService;
    }

    @Transactional(readOnly = true)
    public List<ApprovisionnementResponse> lister() {
        return approvisionnementRepository.findAllByOrderByDateApprovisionnementDescIdDesc().stream()
            .map(ApprovisionnementMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ApprovisionnementResponse obtenir(Long id) {
        return ApprovisionnementMapper.toResponse(trouver(id));
    }

    public ApprovisionnementResponse creer(ApprovisionnementRequest request) {
        verifierNumeroDisponible(request.numero(), null);
        Approvisionnement approvisionnement = new Approvisionnement();
        approvisionnement.setStatut(StatutApprovisionnement.BROUILLON);
        approvisionnement.setLignes(new java.util.ArrayList<>());
        approvisionnement.setFournisseur(trouverFournisseur(request.fournisseurId()));
        ApprovisionnementMapper.mettreAJourApprovisionnement(
            approvisionnement, request, approvisionnement.getFournisseur());
        rattacherArticles(approvisionnement, request.lignes());
        return ApprovisionnementMapper.toResponse(approvisionnementRepository.save(approvisionnement));
    }

    public ApprovisionnementResponse modifier(Long id, ApprovisionnementRequest request) {
        Approvisionnement approvisionnement = trouver(id);
        verifierBrouillon(approvisionnement);
        verifierNumeroDisponible(request.numero(), id);
        Fournisseur fournisseur = trouverFournisseur(request.fournisseurId());
        ApprovisionnementMapper.mettreAJourApprovisionnement(approvisionnement, request, fournisseur);
        rattacherArticles(approvisionnement, request.lignes());
        return ApprovisionnementMapper.toResponse(approvisionnementRepository.save(approvisionnement));
    }

    public void supprimer(Long id) {
        Approvisionnement approvisionnement = trouver(id);
        verifierBrouillon(approvisionnement);
        approvisionnementRepository.delete(approvisionnement);
    }

    public ApprovisionnementResponse valider(Long id, String username) {
        Approvisionnement approvisionnement = trouver(id);
        verifierBrouillon(approvisionnement);
        if (approvisionnement.getLignes() == null || approvisionnement.getLignes().isEmpty()) {
            throw new ConflitRessourceException("Un bon sans ligne ne peut pas être validé");
        }

        Map<Long, Article> articlesVerrouilles = new HashMap<>();
        approvisionnement.getLignes().stream()
            .map(ligne -> ligne.getArticle().getId())
            .distinct()
            .sorted()
            .forEach(articleId -> {
                Article article = articleRepository.findByIdForUpdate(articleId)
                    .orElseThrow(() -> new RessourceIntrouvableException("Article introuvable: " + articleId));
                articlesVerrouilles.put(articleId, article);
            });

        for (LigneApprovisionnement ligne : approvisionnement.getLignes()) {
            Article article = articlesVerrouilles.get(ligne.getArticle().getId());
            int stockAvant = article.getQuantiteStock();
            int stockApres;
            try {
                stockApres = Math.addExact(stockAvant, ligne.getQuantite());
            } catch (ArithmeticException exception) {
                throw new ConflitRessourceException(
                    "La quantité dépasserait la capacité de stock pour l'article " + article.getReference());
            }
            article.setQuantiteStock(stockApres);
            article.setPrixAchat(ligne.getPrixUnitaire());
            creerMouvement(approvisionnement, ligne, article, stockAvant, stockApres);
        }

        approvisionnement.setStatut(StatutApprovisionnement.RECU);
        articleRepository.saveAll(articlesVerrouilles.values());
        approvisionnementRepository.save(approvisionnement);
        journalActiviteService.enregistrer("VALIDATION", "APPROVISIONNEMENT", approvisionnement.getId(),
            "Réception du bon " + approvisionnement.getNumero(), username);
        return ApprovisionnementMapper.toResponse(approvisionnement);
    }

    private void creerMouvement(
        Approvisionnement approvisionnement,
        LigneApprovisionnement ligne,
        Article article,
        int stockAvant,
        int stockApres
    ) {
        MouvementStock mouvement = new MouvementStock();
        mouvement.setType(TypeMouvementStock.ENTREE_APPROVISIONNEMENT);
        mouvement.setQuantite(ligne.getQuantite());
        mouvement.setStockAvant(stockAvant);
        mouvement.setStockApres(stockApres);
        mouvement.setDateMouvement(java.time.LocalDateTime.now());
        mouvement.setReferenceDocument(approvisionnement.getNumero());
        mouvement.setArticle(article);
        mouvement.setEffectuePar(approvisionnement.getCreePar());
        mouvement.setSessionCaisse(approvisionnement.getSessionCaisse());
        mouvement.setApprovisionnement(approvisionnement);
        mouvementStockRepository.save(mouvement);
    }

    private void rattacherArticles(Approvisionnement approvisionnement, List<LigneApprovisionnementRequest> lignes) {
        for (int index = 0; index < lignes.size(); index++) {
            LigneApprovisionnement ligne = approvisionnement.getLignes().get(index);
            ligne.setArticle(trouverArticle(lignes.get(index).articleId()));
            ligne.setApprovisionnement(approvisionnement);
        }
    }

    private void verifierNumeroDisponible(String numero, Long idIgnore) {
        approvisionnementRepository.findByNumeroIgnoreCase(numero.trim()).ifPresent(existing -> {
            if (!existing.getId().equals(idIgnore)) {
                throw new ConflitRessourceException("Un bon d'approvisionnement portant ce numéro existe déjà");
            }
        });
    }

    private void verifierBrouillon(Approvisionnement approvisionnement) {
        if (approvisionnement.getStatut() != StatutApprovisionnement.BROUILLON) {
            throw new ConflitRessourceException(
                "Seul un brouillon peut être modifié, supprimé ou validé");
        }
    }

    private Fournisseur trouverFournisseur(Long id) {
        return fournisseurRepository.findById(id)
            .orElseThrow(() -> new RessourceIntrouvableException("Fournisseur introuvable: " + id));
    }

    private Article trouverArticle(Long id) {
        return articleRepository.findById(id)
            .orElseThrow(() -> new RessourceIntrouvableException("Article introuvable: " + id));
    }

    private Approvisionnement trouver(Long id) {
        return approvisionnementRepository.findById(id)
            .orElseThrow(() -> new RessourceIntrouvableException("Approvisionnement introuvable: " + id));
    }
}
