package com.gesstock.gesstock.inventaires.service;

import com.gesstock.gesstock.common.exception.ConflitRessourceException;
import com.gesstock.gesstock.common.exception.RequeteInvalideException;
import com.gesstock.gesstock.common.exception.RessourceIntrouvableException;
import com.gesstock.gesstock.entities.Article;
import com.gesstock.gesstock.entities.CategorieArticle;
import com.gesstock.gesstock.entities.Inventaire;
import com.gesstock.gesstock.entities.LigneInventaire;
import com.gesstock.gesstock.entities.MotifAjustementStock;
import com.gesstock.gesstock.entities.MouvementStock;
import com.gesstock.gesstock.entities.PerimetreInventaire;
import com.gesstock.gesstock.entities.StatutInventaire;
import com.gesstock.gesstock.entities.TypeMouvementStock;
import com.gesstock.gesstock.entities.Utilisateur;
import com.gesstock.gesstock.inventaires.dto.InventaireRequest;
import com.gesstock.gesstock.inventaires.dto.InventaireResponse;
import com.gesstock.gesstock.inventaires.dto.LigneInventaireRequest;
import com.gesstock.gesstock.inventaires.dto.LigneInventaireResponse;
import com.gesstock.gesstock.journal.service.JournalActiviteService;
import com.gesstock.gesstock.repositories.ArticleRepository;
import com.gesstock.gesstock.repositories.CategorieArticleRepository;
import com.gesstock.gesstock.repositories.InventaireRepository;
import com.gesstock.gesstock.repositories.LigneInventaireRepository;
import com.gesstock.gesstock.repositories.MouvementStockRepository;
import com.gesstock.gesstock.repositories.UtilisateurRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gesstock.gesstock.stock.Quantites;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Inventaire physique : ouverture (photographie du stock théorique et du coût moyen), comptage, soumission, validation par
 * une AUTRE personne, annulation.
 *
 * <p>Les ventes continuent pendant le comptage. À la validation on applique donc l'ÉCART (compté - théorique) au stock du
 * moment, pas la valeur comptée : une vente faite entre l'ouverture et la validation n'est jamais écrasée. Si l'écart ferait
 * passer un stock sous zéro, toute la validation est refusée et l'article est à recompter.
 */
@Service
@Transactional
public class InventaireService {

    public static final int TAILLE_MAX_PAGE = 500;
    private static final Set<StatutInventaire> EN_COURS = EnumSet.of(StatutInventaire.EN_SAISIE, StatutInventaire.SOUMIS);
    private static final DateTimeFormatter FORMAT_CODE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final InventaireRepository inventaireRepository;
    private final LigneInventaireRepository ligneRepository;
    private final ArticleRepository articleRepository;
    private final CategorieArticleRepository categorieRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final MouvementStockRepository mouvementStockRepository;
    private final JournalActiviteService journalActiviteService;

    public InventaireService(
        InventaireRepository inventaireRepository,
        LigneInventaireRepository ligneRepository,
        ArticleRepository articleRepository,
        CategorieArticleRepository categorieRepository,
        UtilisateurRepository utilisateurRepository,
        MouvementStockRepository mouvementStockRepository,
        JournalActiviteService journalActiviteService
    ) {
        this.inventaireRepository = inventaireRepository;
        this.ligneRepository = ligneRepository;
        this.articleRepository = articleRepository;
        this.categorieRepository = categorieRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.mouvementStockRepository = mouvementStockRepository;
        this.journalActiviteService = journalActiviteService;
    }

    // ------------------------------------------------------------------ consultation

    @Transactional(readOnly = true)
    public List<InventaireResponse> lister() {
        return inventaireRepository.findAllByOrderByIdDesc().stream().map(this::reponse).toList();
    }

    @Transactional(readOnly = true)
    public InventaireResponse obtenir(Long id) {
        return reponse(trouver(id));
    }

    public record PageLignes(List<LigneInventaireResponse> lignes, long total) {}

    @Transactional(readOnly = true)
    public PageLignes lignes(Long id, boolean ecartsSeulement, int page, int taille) {
        trouver(id);
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(taille, 1), TAILLE_MAX_PAGE));
        Page<LigneInventaire> resultat = ecartsSeulement ? ligneRepository.ecarts(id, pageable) : ligneRepository.lignes(id, pageable);
        return new PageLignes(resultat.getContent().stream().map(InventaireService::reponse).toList(), resultat.getTotalElements());
    }

    /** Toutes les lignes, pour la feuille de comptage exportée. */
    @Transactional(readOnly = true)
    public List<LigneInventaireResponse> toutesLesLignes(Long id) {
        trouver(id);
        return ligneRepository.toutes(id).stream().map(InventaireService::reponse).toList();
    }

    // ------------------------------------------------------------------ cycle de vie

    public InventaireResponse ouvrir(InventaireRequest request, String username) {
        Utilisateur ouvreur = utilisateurActif(username);
        inventaireRepository.findFirstByStatutIn(EN_COURS).ifPresent(enCours -> {
            throw new ConflitRessourceException("Un inventaire est déjà en cours (" + enCours.getCode()
                + ") : validez-le ou annulez-le avant d'en ouvrir un autre");
        });

        CategorieArticle categorie = null;
        List<Article> articles;
        if (request.perimetre() == PerimetreInventaire.CATEGORIE) {
            if (request.categorieId() == null) {
                throw new RequeteInvalideException("Choisissez la catégorie à inventorier");
            }
            categorie = categorieRepository.findById(request.categorieId())
                .orElseThrow(() -> new RessourceIntrouvableException("Catégorie introuvable: " + request.categorieId()));
            Set<Long> categories = categorieEtDescendantes(categorie.getId());
            articles = articleRepository.findAll().stream()
                .filter(article -> article.getCategorie() != null && categories.contains(article.getCategorie().getId()))
                .toList();
        } else {
            articles = articleRepository.findAll();
        }
        if (articles.isEmpty()) {
            throw new ConflitRessourceException("Aucun article à inventorier pour ce périmètre");
        }

        Inventaire inventaire = new Inventaire();
        LocalDateTime maintenant = LocalDateTime.now();
        String prefixe = "INV-" + FORMAT_CODE.format(maintenant) + "-";
        inventaire.setCode(prefixe + String.format("%03d", inventaireRepository.countByCodeStartingWith(prefixe) + 1));
        inventaire.setStatut(StatutInventaire.EN_SAISIE);
        inventaire.setPerimetre(request.perimetre());
        inventaire.setCategorie(categorie);
        inventaire.setCommentaire(request.commentaire() == null || request.commentaire().isBlank() ? null : request.commentaire().trim());
        inventaire.setOuvertLe(maintenant);
        inventaire.setOuvertPar(ouvreur);
        articles.stream().sorted(Comparator.comparing(Article::getNom, String.CASE_INSENSITIVE_ORDER)).forEach(article -> {
            LigneInventaire ligne = new LigneInventaire();
            ligne.setInventaire(inventaire);
            ligne.setArticle(article);
            ligne.setStockTheorique(article.getQuantiteStock());
            ligne.setCoutUnitaire(article.getCmp());
            inventaire.getLignes().add(ligne);
        });
        Inventaire enregistre = inventaireRepository.saveAndFlush(inventaire);
        journalActiviteService.enregistrer("OUVERTURE", "INVENTAIRE", enregistre.getId(),
            "Inventaire " + enregistre.getCode() + " ouvert (" + articles.size() + " article(s), "
                + (categorie == null ? "tout le catalogue" : "catégorie " + categorie.getNom()) + ")", username);
        return reponse(enregistre);
    }

    public LigneInventaireResponse saisir(Long inventaireId, Long articleId, LigneInventaireRequest request, String username) {
        Inventaire inventaire = verrouiller(inventaireId);
        exiger(inventaire, StatutInventaire.EN_SAISIE, "Le comptage ne se modifie que tant que l'inventaire est en saisie");
        LigneInventaire ligne = ligneRepository.findByInventaireIdAndArticleId(inventaireId, articleId)
            .orElseThrow(() -> new RessourceIntrouvableException("Cet article ne fait pas partie de l'inventaire " + inventaire.getCode()));
        ligne.setStockCompte(request.stockCompte());
        ligne.setMotif(request.motif());
        ligne.setCommentaire(request.commentaire() == null || request.commentaire().isBlank() ? null : request.commentaire().trim());
        return reponse(ligneRepository.save(ligne));
    }

    public InventaireResponse soumettre(Long id, String username) {
        Inventaire inventaire = verrouiller(id);
        exiger(inventaire, StatutInventaire.EN_SAISIE, "Seul un inventaire en saisie peut être soumis");
        List<LigneInventaire> comptees = ligneRepository.toutes(id).stream().filter(ligne -> ligne.getStockCompte() != null).toList();
        if (comptees.isEmpty()) {
            throw new ConflitRessourceException("Comptez au moins un article avant de soumettre l'inventaire");
        }
        List<String> sansMotif = comptees.stream()
            .filter(ligne -> ligne.ecart().signum() != 0)
            .filter(ligne -> ligne.getMotif() == null
                || (ligne.getMotif() == MotifAjustementStock.AUTRE && (ligne.getCommentaire() == null || ligne.getCommentaire().isBlank())))
            .map(ligne -> ligne.getArticle().getReference())
            .toList();
        if (!sansMotif.isEmpty()) {
            throw new ConflitRessourceException("Précisez le motif de l'écart (et un commentaire pour « Autre ») pour : "
                + sansMotif.stream().limit(5).collect(Collectors.joining(", ")) + (sansMotif.size() > 5 ? "…" : ""));
        }
        inventaire.setStatut(StatutInventaire.SOUMIS);
        inventaire.setSoumisLe(LocalDateTime.now());
        journalActiviteService.enregistrer("SOUMISSION", "INVENTAIRE", id,
            "Inventaire " + inventaire.getCode() + " soumis à validation", username);
        return reponse(inventaire);
    }

    /** Renvoie un inventaire soumis en saisie (un comptage à corriger) : possible tant qu'il n'est pas validé. */
    public InventaireResponse reprendre(Long id, String username) {
        Inventaire inventaire = verrouiller(id);
        exiger(inventaire, StatutInventaire.SOUMIS, "Seul un inventaire soumis peut être repris en saisie");
        inventaire.setStatut(StatutInventaire.EN_SAISIE);
        inventaire.setSoumisLe(null);
        journalActiviteService.enregistrer("REPRISE", "INVENTAIRE", id,
            "Inventaire " + inventaire.getCode() + " repris en saisie", username);
        return reponse(inventaire);
    }

    public InventaireResponse annuler(Long id, String username) {
        Inventaire inventaire = verrouiller(id);
        if (!inventaire.getStatut().enCours()) {
            throw new ConflitRessourceException("Seul un inventaire en cours peut être annulé");
        }
        inventaire.setStatut(StatutInventaire.ANNULE);
        journalActiviteService.enregistrer("ANNULATION", "INVENTAIRE", id,
            "Inventaire " + inventaire.getCode() + " annulé, le stock n'a pas été modifié", username);
        return reponse(inventaire);
    }

    public InventaireResponse valider(Long id, String username) {
        Inventaire inventaire = verrouiller(id);
        exiger(inventaire, StatutInventaire.SOUMIS, "Seul un inventaire soumis peut être validé");
        Utilisateur validateur = utilisateurActif(username);
        if (validateur.getUsername().equalsIgnoreCase(inventaire.getOuvertPar().getUsername())) {
            throw new ConflitRessourceException("L'inventaire doit être validé par une autre personne que celle qui l'a ouvert");
        }

        List<LigneInventaire> aAppliquer = ligneRepository.toutes(id).stream()
            .filter(ligne -> ligne.getStockCompte() != null && ligne.ecart().signum() != 0)
            .sorted(Comparator.comparing(ligne -> ligne.getArticle().getId()))
            .toList();

        // verrous pris dans l'ordre des identifiants : deux opérations concurrentes ne peuvent pas s'interbloquer
        Map<Long, Article> articles = new HashMap<>();
        for (LigneInventaire ligne : aAppliquer) {
            Long articleId = ligne.getArticle().getId();
            articles.computeIfAbsent(articleId, identifiant -> articleRepository.findByIdForUpdate(identifiant)
                .orElseThrow(() -> new RessourceIntrouvableException("Article introuvable: " + identifiant)));
        }

        // Première passe : tout vérifier AVANT de rien modifier. Un seul article à recompter suffit à refuser la validation entière.
        for (LigneInventaire ligne : aAppliquer) {
            Article article = articles.get(ligne.getArticle().getId());
            BigDecimal stockAvant = article.getQuantiteStock();
            if (stockAvant.add(ligne.ecart()).signum() < 0) {
                throw new ConflitRessourceException("Recomptez l'article " + article.getReference() + " : des ventes ont eu lieu "
                    + "depuis l'ouverture de l'inventaire (stock actuel " + Quantites.texte(stockAvant) + ", écart compté "
                    + Quantites.texte(ligne.ecart()) + ")");
            }
        }

        BigDecimal valeurTotale = BigDecimal.ZERO;
        for (LigneInventaire ligne : aAppliquer) {
            Article article = articles.get(ligne.getArticle().getId());
            BigDecimal ecart = ligne.ecart();
            BigDecimal stockAvant = article.getQuantiteStock();
            BigDecimal stockApres = stockAvant.add(ecart);
            article.setQuantiteStock(stockApres);
            ligne.setEcartApplique(ecart);
            valeurTotale = valeurTotale.add(Quantites.montant(ligne.getCoutUnitaire(), ecart));

            MouvementStock mouvement = new MouvementStock();
            mouvement.setType(ecart.signum() > 0 ? TypeMouvementStock.AJUSTEMENT_POSITIF : TypeMouvementStock.AJUSTEMENT_NEGATIF);
            mouvement.setQuantite(ecart.abs());
            mouvement.setStockAvant(stockAvant);
            mouvement.setStockApres(stockApres);
            mouvement.setCoutUnitaire(ligne.getCoutUnitaire());
            mouvement.setDateMouvement(LocalDateTime.now());
            mouvement.setReferenceDocument(inventaire.getCode());
            mouvement.setCommentaire(commentaireMouvement(inventaire, ligne));
            mouvement.setArticle(article);
            mouvement.setEffectuePar(validateur);
            mouvement.setInventaire(inventaire);
            mouvementStockRepository.save(mouvement);
        }
        articleRepository.saveAll(articles.values());

        inventaire.setStatut(StatutInventaire.VALIDE);
        inventaire.setValideLe(LocalDateTime.now());
        inventaire.setValidePar(validateur);
        journalActiviteService.enregistrer("VALIDATION", "INVENTAIRE", id,
            "Inventaire " + inventaire.getCode() + " validé : " + aAppliquer.size() + " article(s) ajusté(s), écart total "
                + valeurTotale.toPlainString() + " (ouvert par " + inventaire.getOuvertPar().getUsername() + ")", username);
        return reponse(inventaire);
    }

    // ------------------------------------------------------------------ outils

    private String commentaireMouvement(Inventaire inventaire, LigneInventaire ligne) {
        String texte = "Inventaire " + inventaire.getCode() + " : " + (ligne.getMotif() == null ? "écart de comptage" : ligne.getMotif().libelle())
            + (ligne.getCommentaire() == null ? "" : " — " + ligne.getCommentaire());
        return texte.length() > 500 ? texte.substring(0, 500) : texte;
    }

    private Set<Long> categorieEtDescendantes(Long racine) {
        List<CategorieArticle> toutes = categorieRepository.findAll();
        Set<Long> retenues = new HashSet<>();
        retenues.add(racine);
        boolean ajout = true;
        while (ajout) { // remonte les parentés jusqu'à stabilité : insensible à la profondeur de l'arbre
            ajout = false;
            for (CategorieArticle categorie : toutes) {
                if (categorie.getParent() != null && retenues.contains(categorie.getParent().getId()) && retenues.add(categorie.getId())) {
                    ajout = true;
                }
            }
        }
        return retenues;
    }

    private Inventaire trouver(Long id) {
        return inventaireRepository.findById(id)
            .orElseThrow(() -> new RessourceIntrouvableException("Inventaire introuvable: " + id));
    }

    /** Verrou pessimiste : deux changements d'état (valider et annuler en même temps) ne se croisent pas. */
    private Inventaire verrouiller(Long id) {
        return inventaireRepository.findByIdForUpdate(id)
            .orElseThrow(() -> new RessourceIntrouvableException("Inventaire introuvable: " + id));
    }

    private void exiger(Inventaire inventaire, StatutInventaire attendu, String message) {
        if (inventaire.getStatut() != attendu) {
            throw new ConflitRessourceException(message + " (statut actuel : " + inventaire.getStatut() + ")");
        }
    }

    private Utilisateur utilisateurActif(String username) {
        Utilisateur utilisateur = utilisateurRepository.findByUsername(username)
            .orElseThrow(() -> new RessourceIntrouvableException("Utilisateur connecté introuvable"));
        if (!utilisateur.isActif()) {
            throw new ConflitRessourceException("Un utilisateur désactivé ne peut pas gérer un inventaire");
        }
        return utilisateur;
    }

    private InventaireResponse reponse(Inventaire inventaire) {
        Object[] totaux = ligneRepository.totaux(inventaire.getId()).get(0);
        BigDecimal valeur = totaux[3] instanceof BigDecimal decimal ? decimal : new BigDecimal(totaux[3].toString());
        return new InventaireResponse(
            inventaire.getId(),
            inventaire.getCode(),
            inventaire.getStatut(),
            inventaire.getPerimetre(),
            inventaire.getCategorie() == null ? null : inventaire.getCategorie().getId(),
            inventaire.getCategorie() == null ? null : inventaire.getCategorie().getNom(),
            inventaire.getCommentaire(),
            inventaire.getOuvertLe(),
            inventaire.getOuvertPar().getUsername(),
            inventaire.getSoumisLe(),
            inventaire.getValideLe(),
            inventaire.getValidePar() == null ? null : inventaire.getValidePar().getUsername(),
            ((Number) totaux[0]).longValue(),
            ((Number) totaux[1]).longValue(),
            ((Number) totaux[2]).longValue(),
            valeur);
    }

    private static LigneInventaireResponse reponse(LigneInventaire ligne) {
        Article article = ligne.getArticle();
        BigDecimal ecart = ligne.ecart();
        return new LigneInventaireResponse(
            article.getId(),
            article.getReference(),
            article.getDesignation(),
            article.getCategorie() == null ? null : article.getCategorie().getNom(),
            article.getUniteMesure() == null ? null : article.getUniteMesure().getLibelle(),
            ligne.getStockTheorique(),
            ligne.getStockCompte(),
            ecart,
            ligne.getCoutUnitaire(),
            ecart == null ? null : Quantites.montant(ligne.getCoutUnitaire(), ecart),
            ligne.getMotif(),
            ligne.getCommentaire(),
            ligne.getEcartApplique());
    }
}
