package com.gesstock.gesstock.fournisseurs.service;

import com.gesstock.gesstock.common.exception.ConflitRessourceException;
import com.gesstock.gesstock.common.exception.RequeteInvalideException;
import com.gesstock.gesstock.common.exception.RessourceIntrouvableException;
import com.gesstock.gesstock.entities.Approvisionnement;
import com.gesstock.gesstock.entities.Article;
import com.gesstock.gesstock.entities.AvoirFournisseur;
import com.gesstock.gesstock.entities.LigneApprovisionnement;
import com.gesstock.gesstock.entities.LigneRetourFournisseur;
import com.gesstock.gesstock.entities.MouvementStock;
import com.gesstock.gesstock.entities.RetourFournisseur;
import com.gesstock.gesstock.entities.StatutApprovisionnement;
import com.gesstock.gesstock.entities.StatutRetourFournisseur;
import com.gesstock.gesstock.entities.TypeMouvementStock;
import com.gesstock.gesstock.entities.Utilisateur;
import com.gesstock.gesstock.fournisseurs.dto.LigneRetourFournisseurRequest;
import com.gesstock.gesstock.fournisseurs.dto.LigneRetourFournisseurResponse;
import com.gesstock.gesstock.fournisseurs.dto.LigneRetournableResponse;
import com.gesstock.gesstock.fournisseurs.dto.RetourFournisseurRequest;
import com.gesstock.gesstock.fournisseurs.dto.RetourFournisseurResponse;
import com.gesstock.gesstock.journal.service.JournalActiviteService;
import com.gesstock.gesstock.repositories.ApprovisionnementRepository;
import com.gesstock.gesstock.repositories.ArticleRepository;
import com.gesstock.gesstock.repositories.LigneApprovisionnementRepository;
import com.gesstock.gesstock.repositories.MouvementStockRepository;
import com.gesstock.gesstock.repositories.RetourFournisseurRepository;
import com.gesstock.gesstock.repositories.UtilisateurRepository;
import com.gesstock.gesstock.stock.CoutMoyenPondere;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Retour de marchandise au fournisseur. Un retour part d'un bon REÇU et d'une ou plusieurs de ses lignes ; il reste brouillon
 * jusqu'à validation par un responsable. La validation :
 * <ul>
 *   <li>compte les unités rendues sur la ligne du bon par un UPDATE conditionnel (jamais plus que reçu, même en concurrence) ;</li>
 *   <li>sort le stock (mouvement RETOUR_FOURNISSEUR au coût du bon) et recalcule le coût moyen ;</li>
 *   <li>crée l'avoir du fournisseur, qui s'impute sur ses dettes (voir {@link CompteFournisseurService}).</li>
 * </ul>
 * Une fois validé, un retour ne s'annule pas. Ordre des verrous : lignes de bon, puis articles (par identifiant croissant),
 * puis fournisseur.
 */
@Service
@Transactional
public class RetourFournisseurService {

    private static final int ECHELLE_MONETAIRE = 4;

    private final RetourFournisseurRepository retourRepository;
    private final ApprovisionnementRepository bonRepository;
    private final LigneApprovisionnementRepository ligneBonRepository;
    private final ArticleRepository articleRepository;
    private final MouvementStockRepository mouvementStockRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final CompteFournisseurService compteService;
    private final JournalActiviteService journalActiviteService;
    private final EntityManager entityManager;

    public RetourFournisseurService(
        RetourFournisseurRepository retourRepository,
        ApprovisionnementRepository bonRepository,
        LigneApprovisionnementRepository ligneBonRepository,
        ArticleRepository articleRepository,
        MouvementStockRepository mouvementStockRepository,
        UtilisateurRepository utilisateurRepository,
        CompteFournisseurService compteService,
        JournalActiviteService journalActiviteService,
        EntityManager entityManager
    ) {
        this.retourRepository = retourRepository;
        this.bonRepository = bonRepository;
        this.ligneBonRepository = ligneBonRepository;
        this.articleRepository = articleRepository;
        this.mouvementStockRepository = mouvementStockRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.compteService = compteService;
        this.journalActiviteService = journalActiviteService;
        this.entityManager = entityManager;
    }

    // --- Lecture ---

    @Transactional(readOnly = true)
    public List<RetourFournisseurResponse> lister() {
        return retourRepository.findAllByOrderByIdDesc().stream().map(RetourFournisseurService::reponse).toList();
    }

    @Transactional(readOnly = true)
    public RetourFournisseurResponse obtenir(Long id) {
        return reponse(trouver(id));
    }

    /** Les lignes d'un bon reçu avec ce qu'on peut encore rendre : de quoi composer un retour. */
    @Transactional(readOnly = true)
    public List<LigneRetournableResponse> lignesRetournables(Long approvisionnementId) {
        return bonRecu(approvisionnementId).getLignes().stream()
            .sorted(Comparator.comparing(LigneApprovisionnement::getId))
            .map(ligne -> new LigneRetournableResponse(
                ligne.getId(),
                ligne.getArticle().getId(),
                ligne.getArticle().getReference(),
                ligne.getArticle().getDesignation(),
                ligne.getQuantite(),
                ligne.getQuantiteRetournee(),
                ligne.getQuantite() - ligne.getQuantiteRetournee(),
                ligne.getPrixUnitaire(),
                ligne.getArticle().getQuantiteStock()))
            .toList();
    }

    // --- Écriture ---

    public RetourFournisseurResponse creer(RetourFournisseurRequest request, String username) {
        Approvisionnement bon = bonRecu(request.approvisionnementId());
        Utilisateur createur = utilisateurActif(username);
        Map<Long, LigneApprovisionnement> lignesDuBon = new HashMap<>();
        bon.getLignes().forEach(ligne -> lignesDuBon.put(ligne.getId(), ligne));

        RetourFournisseur retour = new RetourFournisseur();
        retour.setNumero(prochainNumero());
        retour.setFournisseur(bon.getFournisseur());
        retour.setApprovisionnement(bon);
        retour.setMotif(request.motif().trim());
        retour.setCreePar(createur);
        retour.setDateCreation(LocalDateTime.now());

        Set<Long> dejaVues = new HashSet<>();
        BigDecimal total = BigDecimal.ZERO;
        for (LigneRetourFournisseurRequest demande : request.lignes()) {
            if (!dejaVues.add(demande.ligneApprovisionnementId())) {
                throw new RequeteInvalideException("Une ligne du bon ne peut figurer qu'une fois dans le retour");
            }
            LigneApprovisionnement ligneBon = lignesDuBon.get(demande.ligneApprovisionnementId());
            if (ligneBon == null) {
                throw new RequeteInvalideException("La ligne " + demande.ligneApprovisionnementId()
                    + " ne fait pas partie du bon " + bon.getNumero());
            }
            int retournable = ligneBon.getQuantite() - ligneBon.getQuantiteRetournee();
            if (demande.quantite() > retournable) {
                throw new ConflitRessourceException("Article " + ligneBon.getArticle().getReference() + " : seulement "
                    + retournable + " unité(s) encore retournable(s) sur le bon " + bon.getNumero() + " (reçu "
                    + ligneBon.getQuantite() + ", déjà rendu " + ligneBon.getQuantiteRetournee() + ")");
            }
            LigneRetourFournisseur ligne = new LigneRetourFournisseur();
            ligne.setRetour(retour);
            ligne.setLigneApprovisionnement(ligneBon);
            ligne.setArticle(ligneBon.getArticle());
            ligne.setQuantite(demande.quantite());
            ligne.setCoutUnitaire(ligneBon.getPrixUnitaire().setScale(ECHELLE_MONETAIRE, RoundingMode.HALF_UP));
            retour.getLignes().add(ligne);
            total = total.add(ligne.montant());
        }
        retour.setMontantTotal(total);
        retourRepository.save(retour);
        journalActiviteService.enregistrer("CREATION", "RETOUR_FOURNISSEUR", retour.getId(),
            "Retour " + retour.getNumero() + " préparé pour " + bon.getFournisseur().getNom() + " (bon " + bon.getNumero()
                + ") : " + total.toPlainString(), username);
        return reponse(retour);
    }

    public RetourFournisseurResponse valider(Long id, String username) {
        RetourFournisseur retour = retourRepository.findByIdForUpdate(id)
            .orElseThrow(() -> new RessourceIntrouvableException("Retour fournisseur introuvable: " + id));
        exiger(retour, "Seul un retour en brouillon peut être validé");
        Utilisateur validateur = utilisateurActif(username);
        List<LigneRetourFournisseur> lignes = retour.getLignes().stream()
            .sorted(Comparator.comparing(ligne -> ligne.getLigneApprovisionnement().getId()))
            .toList();

        // 1. Unités rendues, ligne de bon par ligne de bon : l'UPDATE conditionnel est le juge, pas une lecture préalable.
        for (LigneRetourFournisseur ligne : lignes) {
            if (ligneBonRepository.retournerQuantite(ligne.getLigneApprovisionnement().getId(), ligne.getQuantite()) == 0) {
                throw new ConflitRessourceException("Article " + ligne.getArticle().getReference() + " : ces unités ont déjà été "
                    + "rendues au fournisseur sur le bon " + retour.getApprovisionnement().getNumero());
            }
            entityManager.refresh(ligne.getLigneApprovisionnement());
        }

        // 2. Verrous des articles dans l'ordre des identifiants : deux opérations concurrentes ne peuvent pas s'interbloquer.
        Map<Long, Article> articles = new HashMap<>();
        lignes.stream().map(ligne -> ligne.getArticle().getId()).distinct().sorted().forEach(articleId ->
            articles.put(articleId, articleRepository.findByIdForUpdate(articleId)
                .orElseThrow(() -> new RessourceIntrouvableException("Article introuvable: " + articleId))));

        // 3. Tout vérifier AVANT de rien modifier : on ne rend que ce qui est encore en rayon.
        Map<Long, Integer> restant = new HashMap<>();
        articles.forEach((articleId, article) -> restant.put(articleId, article.getQuantiteStock()));
        for (LigneRetourFournisseur ligne : lignes) {
            Article article = articles.get(ligne.getArticle().getId());
            int reste = restant.merge(article.getId(), -ligne.getQuantite(), Integer::sum);
            if (reste < 0) {
                throw new ConflitRessourceException("Stock insuffisant pour rendre " + ligne.getQuantite() + " × "
                    + article.getReference() + " : il n'en reste que " + article.getQuantiteStock()
                    + " en rayon (le reste a été vendu ou ajusté)");
            }
        }

        // 4. Sortie de stock au coût du bon, et coût moyen recalculé.
        Approvisionnement bon = retour.getApprovisionnement();
        for (LigneRetourFournisseur ligne : lignes) {
            Article article = articles.get(ligne.getArticle().getId());
            int stockAvant = article.getQuantiteStock();
            int stockApres = stockAvant - ligne.getQuantite();
            article.setCmp(CoutMoyenPondere.apresRetourFournisseur(stockAvant, article.getCmp(), ligne.getQuantite(), ligne.getCoutUnitaire()));
            article.setQuantiteStock(stockApres);

            MouvementStock mouvement = new MouvementStock();
            mouvement.setType(TypeMouvementStock.RETOUR_FOURNISSEUR);
            mouvement.setQuantite(ligne.getQuantite());
            mouvement.setStockAvant(stockAvant);
            mouvement.setStockApres(stockApres);
            mouvement.setCoutUnitaire(ligne.getCoutUnitaire());
            mouvement.setDateMouvement(LocalDateTime.now());
            mouvement.setReferenceDocument(retour.getNumero());
            mouvement.setCommentaire(limiter("Retour à " + retour.getFournisseur().getNom() + " (bon " + bon.getNumero() + ") : "
                + retour.getMotif(), 500));
            mouvement.setArticle(article);
            mouvement.setEffectuePar(validateur);
            mouvement.setApprovisionnement(bon);
            mouvement.setRetourFournisseur(retour);
            mouvementStockRepository.save(mouvement);
        }
        articleRepository.saveAll(articles.values());

        // 5. L'avoir du fournisseur (un retour de valeur nulle n'en crée pas).
        AvoirFournisseur avoir = null;
        if (retour.getMontantTotal().signum() > 0) {
            avoir = compteService.emettreAvoir(retour.getFournisseur().getId(), retour.getMontantTotal(),
                limiter("Retour " + retour.getNumero() + " (bon " + bon.getNumero() + ") : " + retour.getMotif(), 300), username);
        }
        retour.setAvoir(avoir);
        retour.setStatut(StatutRetourFournisseur.VALIDE);
        retour.setDateValidation(LocalDateTime.now());
        retour.setValidePar(validateur);
        retourRepository.save(retour);
        journalActiviteService.enregistrer("VALIDATION", "RETOUR_FOURNISSEUR", retour.getId(),
            "Retour " + retour.getNumero() + " validé : " + lignes.size() + " ligne(s) rendue(s) à " + retour.getFournisseur().getNom()
                + ", avoir de " + retour.getMontantTotal().toPlainString(), username);
        return reponse(retour);
    }

    public RetourFournisseurResponse annuler(Long id, String username) {
        RetourFournisseur retour = retourRepository.findByIdForUpdate(id)
            .orElseThrow(() -> new RessourceIntrouvableException("Retour fournisseur introuvable: " + id));
        exiger(retour, "Seul un retour en brouillon peut être annulé : un retour validé a déjà sorti le stock et créé un avoir");
        retour.setStatut(StatutRetourFournisseur.ANNULE);
        retourRepository.save(retour);
        journalActiviteService.enregistrer("ANNULATION", "RETOUR_FOURNISSEUR", retour.getId(),
            "Retour " + retour.getNumero() + " annulé avant validation", username);
        return reponse(retour);
    }

    // --- Utilitaires ---

    private String prochainNumero() {
        String prefixe = "RF-" + LocalDateTime.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-";
        return prefixe + String.format("%03d", retourRepository.countByNumeroStartingWith(prefixe) + 1);
    }

    private Approvisionnement bonRecu(Long id) {
        Approvisionnement bon = bonRepository.findById(id)
            .orElseThrow(() -> new RessourceIntrouvableException("Approvisionnement introuvable: " + id));
        if (bon.getStatut() != StatutApprovisionnement.RECU) {
            throw new ConflitRessourceException("Seul un bon reçu peut faire l'objet d'un retour au fournisseur");
        }
        return bon;
    }

    private RetourFournisseur trouver(Long id) {
        return retourRepository.findById(id)
            .orElseThrow(() -> new RessourceIntrouvableException("Retour fournisseur introuvable: " + id));
    }

    private void exiger(RetourFournisseur retour, String message) {
        if (retour.getStatut() != StatutRetourFournisseur.BROUILLON) {
            throw new ConflitRessourceException(message);
        }
    }

    private Utilisateur utilisateurActif(String username) {
        Utilisateur utilisateur = utilisateurRepository.findByUsername(username)
            .orElseThrow(() -> new RessourceIntrouvableException("Utilisateur connecté introuvable"));
        if (!utilisateur.isActif()) {
            throw new ConflitRessourceException("Un utilisateur désactivé ne peut pas enregistrer cette opération");
        }
        return utilisateur;
    }

    private static String limiter(String texte, int longueurMax) {
        return texte.length() > longueurMax ? texte.substring(0, longueurMax) : texte;
    }

    private static RetourFournisseurResponse reponse(RetourFournisseur retour) {
        return new RetourFournisseurResponse(
            retour.getId(),
            retour.getNumero(),
            retour.getStatut(),
            retour.getFournisseur().getId(),
            retour.getFournisseur().getNom(),
            retour.getApprovisionnement().getId(),
            retour.getApprovisionnement().getNumero(),
            retour.getMotif(),
            retour.getDateCreation(),
            retour.getCreePar().getUsername(),
            retour.getDateValidation(),
            retour.getValidePar() == null ? null : retour.getValidePar().getUsername(),
            retour.getMontantTotal(),
            retour.getAvoir() == null ? null : retour.getAvoir().getId(),
            retour.getLignes().stream().map(ligne -> new LigneRetourFournisseurResponse(
                ligne.getLigneApprovisionnement().getId(),
                ligne.getArticle().getId(),
                ligne.getArticle().getReference(),
                ligne.getArticle().getDesignation(),
                ligne.getQuantite(),
                ligne.getCoutUnitaire(),
                ligne.montant())).toList());
    }
}
