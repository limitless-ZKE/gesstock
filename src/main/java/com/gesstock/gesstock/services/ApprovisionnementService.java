package com.gesstock.gesstock.services;

import com.gesstock.gesstock.entities.Article;
import com.gesstock.gesstock.entities.Approvisionnement;
import com.gesstock.gesstock.entities.LigneApprovisionnement;
import com.gesstock.gesstock.repositories.ArticleRepository;
import com.gesstock.gesstock.repositories.ApprovisionnementRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ApprovisionnementService {

    @Autowired
    private ApprovisionnementRepository approvisionnementRepository;

    @Autowired
    private ArticleRepository articleRepository;

    public List<Approvisionnement> listerTous() {
        return approvisionnementRepository.findAll();
    }

    public Optional<Approvisionnement> trouverParId(Long id) {
        return approvisionnementRepository.findById(id);
    }

    @Transactional
    public Approvisionnement sauvegarderApprovisionnement(Approvisionnement approvisionnement) {
        boolean nouvelApprovisionnement = approvisionnement.getId() == null;
        if (nouvelApprovisionnement && (approvisionnement.getNumero() == null
                || approvisionnement.getNumero().isBlank())) {
            approvisionnement.setNumero("APP-" + UUID.randomUUID());
        }

        if (approvisionnement.getLignes() != null) {
            for (LigneApprovisionnement ligne : approvisionnement.getLignes()) {
                ligne.setApprovisionnement(approvisionnement);

                if (nouvelApprovisionnement && ligne.getArticle() != null && ligne.getArticle().getId() != null) {
                    Article article = articleRepository.findById(ligne.getArticle().getId())
                            .orElseThrow(() -> new RuntimeException("Article introuvable"));
                    
                    article.setQuantiteStock(article.getQuantiteStock() + ligne.getQuantite());
                    articleRepository.save(article);
                }
            }
        }

        return approvisionnementRepository.save(approvisionnement);
    }

    public void supprimer(Long id) {
        approvisionnementRepository.deleteById(id);
    }
}