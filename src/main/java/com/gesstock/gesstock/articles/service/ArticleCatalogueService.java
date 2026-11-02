package com.gesstock.gesstock.articles.service;

import com.gesstock.gesstock.articles.dto.ArticleRequest;
import com.gesstock.gesstock.articles.dto.ArticleResponse;
import com.gesstock.gesstock.common.exception.ConflitRessourceException;
import com.gesstock.gesstock.common.exception.RequeteInvalideException;
import com.gesstock.gesstock.common.exception.RessourceIntrouvableException;
import com.gesstock.gesstock.articles.mapper.CatalogueMapper;
import com.gesstock.gesstock.entities.Article;
import com.gesstock.gesstock.entities.CategorieArticle;
import com.gesstock.gesstock.entities.MouvementStock;
import com.gesstock.gesstock.entities.TypeMouvementStock;
import com.gesstock.gesstock.entities.UniteMesure;
import com.gesstock.gesstock.journal.service.JournalActiviteService;
import com.gesstock.gesstock.repositories.ArticleRepository;
import com.gesstock.gesstock.repositories.CategorieArticleRepository;
import com.gesstock.gesstock.repositories.MouvementStockRepository;
import com.gesstock.gesstock.repositories.UniteMesureRepository;
import com.gesstock.gesstock.repositories.UtilisateurRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gesstock.gesstock.stock.Quantites;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
public class ArticleCatalogueService {

    private final ArticleRepository articleRepository;
    private final CategorieArticleRepository categorieRepository;
    private final UniteMesureRepository uniteMesureRepository;
    private final MouvementStockRepository mouvementStockRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final JournalActiviteService journalService;

    public ArticleCatalogueService(
        ArticleRepository articleRepository,
        CategorieArticleRepository categorieRepository,
        UniteMesureRepository uniteMesureRepository,
        MouvementStockRepository mouvementStockRepository,
        UtilisateurRepository utilisateurRepository,
        JournalActiviteService journalService
    ) {
        this.articleRepository = articleRepository;
        this.categorieRepository = categorieRepository;
        this.uniteMesureRepository = uniteMesureRepository;
        this.mouvementStockRepository = mouvementStockRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.journalService = journalService;
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
        return creer(request, null);
    }

    /** Crée l'article ; un stock de départ non nul laisse un mouvement STOCK_INITIAL (la somme des mouvements explique le stock). */
    public ArticleResponse creer(ArticleRequest request, String username) {
        verifierReferenceDisponible(request.reference(), null);
        Article article = new Article();
        remplir(article, request, true);
        Article enregistre = articleRepository.save(article);
        journalService.enregistrer("CREATION", "ARTICLE", enregistre.getId(), "Article " + enregistre.getReference() + " « "
            + enregistre.getDesignation() + " » créé : prix de vente " + texte(enregistre.getPrix()) + ", prix d'achat "
            + texte(enregistre.getPrixAchat())
            + (enregistre.getQuantiteStock().signum() > 0 ? ", stock de départ " + Quantites.texte(enregistre.getQuantiteStock()) : ""),
            username);
        if (enregistre.getQuantiteStock().signum() > 0) {
            MouvementStock mouvement = new MouvementStock();
            mouvement.setType(TypeMouvementStock.STOCK_INITIAL);
            mouvement.setQuantite(enregistre.getQuantiteStock());
            mouvement.setStockAvant(BigDecimal.ZERO);
            mouvement.setStockApres(enregistre.getQuantiteStock());
            mouvement.setCoutUnitaire(enregistre.getCmp());
            mouvement.setDateMouvement(LocalDateTime.now());
            mouvement.setReferenceDocument("STOCK-INITIAL");
            mouvement.setCommentaire("Stock de départ saisi à la création de l'article");
            mouvement.setArticle(enregistre);
            if (username != null) {
                utilisateurRepository.findByUsername(username).ifPresent(mouvement::setEffectuePar);
            }
            mouvementStockRepository.save(mouvement);
        }
        return CatalogueMapper.toResponse(enregistre);
    }

    public ArticleResponse modifier(Long id, ArticleRequest request) {
        Article article = trouver(id);
        verifierReferenceDisponible(request.reference(), id);
        // Le stock ne se corrige JAMAIS ici : toute variation doit laisser un mouvement (ajustement de stock), sinon
        // la somme des mouvements ne correspond plus au stock et l'erreur ou la fraude devient invisible.
        if (request.quantiteStock() != null && request.quantiteStock().compareTo(article.getQuantiteStock()) != 0) {
            throw new RequeteInvalideException(
                "Le stock ne se modifie pas depuis la fiche article : utilisez « Ajuster le stock » (motif obligatoire)");
        }
        String avant = article.getReference() + " « " + article.getDesignation() + " »";
        List<String> changements = new ArrayList<>();
        String reference = article.getReference();
        String designation = article.getDesignation();
        BigDecimal prix = article.getPrix();
        BigDecimal prixAchat = article.getPrixAchat();
        int seuil = article.getSeuilAlerte();
        String categorie = article.getCategorie() == null ? "aucune" : article.getCategorie().getNom();
        remplir(article, request, false);
        noter(changements, "référence", reference, article.getReference());
        noter(changements, "désignation", designation, article.getDesignation());
        if (differents(prix, article.getPrix())) changements.add("prix de vente " + texte(prix) + " → " + texte(article.getPrix()));
        if (differents(prixAchat, article.getPrixAchat())) changements.add("prix d'achat " + texte(prixAchat) + " → " + texte(article.getPrixAchat()));
        if (seuil != article.getSeuilAlerte()) changements.add("seuil d'alerte " + seuil + " → " + article.getSeuilAlerte());
        noter(changements, "catégorie", categorie, article.getCategorie() == null ? "aucune" : article.getCategorie().getNom());
        Article enregistre = articleRepository.save(article);
        if (!changements.isEmpty()) {
            journalService.enregistrer("MODIFICATION", "ARTICLE", id, "Article " + avant + " : " + String.join(" ; ", changements), null);
        }
        return CatalogueMapper.toResponse(enregistre);
    }

    public void supprimer(Long id) {
        Article article = trouver(id);
        articleRepository.delete(article);
        journalService.enregistrer("SUPPRESSION", "ARTICLE", id,
            "Article " + article.getReference() + " « " + article.getDesignation() + " » supprimé (prix de vente " + texte(article.getPrix()) + ")", null);
    }

    /** « désignation « Riz » → « Riz local » », seulement si la valeur a changé. */
    private static void noter(List<String> changements, String champ, String avant, String apres) {
        if (!java.util.Objects.equals(avant, apres)) {
            changements.add(champ + " « " + avant + " » → « " + apres + " »");
        }
    }

    private static boolean differents(BigDecimal avant, BigDecimal apres) {
        return avant == null ? apres != null : apres == null || avant.compareTo(apres) != 0;
    }

    /** 1300.0000 → « 1300 » : le montant tel qu'on l'écrit, sans zéros inutiles. */
    private static String texte(BigDecimal montant) {
        return montant == null ? "—" : montant.stripTrailingZeros().toPlainString();
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
