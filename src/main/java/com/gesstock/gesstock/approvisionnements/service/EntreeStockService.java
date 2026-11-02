package com.gesstock.gesstock.approvisionnements.service;

import com.gesstock.gesstock.approvisionnements.dto.ApprovisionnementRequest;
import com.gesstock.gesstock.approvisionnements.dto.ApprovisionnementResponse;
import com.gesstock.gesstock.approvisionnements.dto.LigneApprovisionnementRequest;
import com.gesstock.gesstock.common.exception.ConflitRessourceException;
import com.gesstock.gesstock.common.exception.RessourceIntrouvableException;
import com.gesstock.gesstock.approvisionnements.mapper.ApprovisionnementMapper;
import com.gesstock.gesstock.entities.*;
import com.gesstock.gesstock.fournisseurs.service.CompteFournisseurService;
import com.gesstock.gesstock.repositories.*;
import com.gesstock.gesstock.stock.CoutMoyenPondere;
import com.gesstock.gesstock.stock.Quantites;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.gesstock.gesstock.journal.service.JournalActiviteService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

@Service
@Transactional
public class EntreeStockService {

    private final ApprovisionnementRepository approvisionnementRepository;
    private final FournisseurRepository fournisseurRepository;
    private final ArticleRepository articleRepository;
    private final MouvementStockRepository mouvementStockRepository;
    private final JournalActiviteService journalActiviteService;
    private final HistoriquePrixAchatRepository historiquePrixAchatRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final CompteFournisseurService compteFournisseurService;
    private final RetourFournisseurRepository retourFournisseurRepository;

    public EntreeStockService(
        ApprovisionnementRepository approvisionnementRepository,
        FournisseurRepository fournisseurRepository,
        ArticleRepository articleRepository,
        MouvementStockRepository mouvementStockRepository,
        JournalActiviteService journalActiviteService,
        HistoriquePrixAchatRepository historiquePrixAchatRepository,
        UtilisateurRepository utilisateurRepository,
        CompteFournisseurService compteFournisseurService,
        RetourFournisseurRepository retourFournisseurRepository
    ) {
        this.compteFournisseurService = compteFournisseurService;
        this.historiquePrixAchatRepository = historiquePrixAchatRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.approvisionnementRepository = approvisionnementRepository;
        this.fournisseurRepository = fournisseurRepository;
        this.articleRepository = articleRepository;
        this.mouvementStockRepository = mouvementStockRepository;
        this.journalActiviteService = journalActiviteService;
        this.retourFournisseurRepository = retourFournisseurRepository;
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

    /**
     * Modifie un bon. Un brouillon se modifie librement. Un bon déjà réceptionné se corrige aussi (demande du client) :
     * le stock, le coût moyen, les prix et la dette envers le fournisseur suivent la correction.
     */
    public ApprovisionnementResponse modifier(Long id, ApprovisionnementRequest request, String username) {
        Approvisionnement approvisionnement = trouver(id);
        verifierNumeroDisponible(request.numero(), id);
        if (approvisionnement.getStatut() == StatutApprovisionnement.RECU) {
            return corrigerBonRecu(approvisionnement, request, username);
        }
        if (approvisionnement.getStatut() == StatutApprovisionnement.ANNULE) {
            throw new ConflitRessourceException("Un bon annulé ne peut pas être modifié");
        }
        verifierBrouillon(approvisionnement);
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

        Map<Long, Article> articlesVerrouilles = verrouiller(approvisionnement.getLignes().stream()
            .map(ligne -> ligne.getArticle().getId()).toList());
        Utilisateur utilisateur = utilisateurRepository.findByUsername(username).orElse(null);

        for (LigneApprovisionnement ligne : approvisionnement.getLignes()) {
            Article article = articlesVerrouilles.get(ligne.getArticle().getId());
            BigDecimal stockAvant = article.getQuantiteStock();
            BigDecimal stockApres = stockAvant.add(ligne.getQuantite());
            // Le coût de l'article est le coût moyen pondéré, recalculé à chaque entrée ; le prix saisi n'est que le dernier prix.
            BigDecimal ancienPrix = article.getPrixAchat();
            BigDecimal ancienCmp = article.getCmp();
            BigDecimal nouveauCmp = CoutMoyenPondere.apresEntree(stockAvant, ancienCmp, ligne.getQuantite(), ligne.getPrixUnitaire());
            article.setQuantiteStock(stockApres);
            article.setPrixAchat(ligne.getPrixUnitaire());
            article.setCmp(nouveauCmp);
            enregistrerHistoriquePrix(article, approvisionnement, utilisateur, ancienPrix, ancienCmp, nouveauCmp, stockAvant,
                ligne.getPrixUnitaire(), ligne.getQuantite());
            creerMouvement(approvisionnement, article, TypeMouvementStock.ENTREE_APPROVISIONNEMENT, ligne.getQuantite(),
                ligne.getPrixUnitaire(), stockAvant, stockApres, null);
            appliquerPrixDeVente(article, ligne.getPrixVente(), approvisionnement, username);
        }

        approvisionnement.setStatut(StatutApprovisionnement.RECU);
        articleRepository.saveAll(articlesVerrouilles.values());
        approvisionnementRepository.save(approvisionnement);
        // Le bon reçu devient une dette envers le fournisseur (et les avoirs qu'il nous doit déjà s'y imputent).
        compteFournisseurService.enregistrerDette(approvisionnement, username);
        journalActiviteService.enregistrer("VALIDATION", "APPROVISIONNEMENT", approvisionnement.getId(),
            "Réception du bon " + approvisionnement.getNumero(), username);
        return ApprovisionnementMapper.toResponse(approvisionnement);
    }

    /** Ce qu'un bon apporte à un article : quantité et valeur (prix d'achat × quantité) de toutes ses lignes. */
    private record Apport(BigDecimal quantite, BigDecimal valeur) {
        static final Apport AUCUN = new Apport(BigDecimal.ZERO, BigDecimal.ZERO);

        Apport plus(BigDecimal quantiteLigne, BigDecimal prixUnitaire) {
            return new Apport(quantite.add(quantiteLigne), valeur.add(Quantites.montant(prixUnitaire, quantiteLigne)));
        }

        /** Coût unitaire moyen de l'apport (zéro s'il n'y a rien). */
        BigDecimal cout() {
            return quantite.signum() == 0 ? BigDecimal.ZERO : valeur.divide(quantite, 4, RoundingMode.HALF_UP);
        }

        boolean memeQue(Apport autre) {
            return quantite.compareTo(autre.quantite) == 0 && valeur.compareTo(autre.valeur) == 0;
        }
    }

    private static Map<Long, Apport> apportsParArticle(List<LigneApprovisionnement> lignes) {
        Map<Long, Apport> apports = new LinkedHashMap<>();
        for (LigneApprovisionnement ligne : lignes) {
            apports.merge(ligne.getArticle().getId(), Apport.AUCUN.plus(ligne.getQuantite(), ligne.getPrixUnitaire()),
                (a, b) -> new Apport(a.quantite().add(b.quantite()), a.valeur().add(b.valeur())));
        }
        return apports;
    }

    /**
     * Corrige un bon déjà réceptionné : on retire du stock ce que l'ancien bon y avait mis (à son coût) et on y met ce que dit
     * le bon corrigé. Refusé si un retour au fournisseur porte sur le bon, ou si la marchandise à retirer a déjà été vendue.
     */
    private ApprovisionnementResponse corrigerBonRecu(Approvisionnement bon, ApprovisionnementRequest request, String username) {
        if (retourFournisseurRepository.existsByApprovisionnementId(bon.getId())) {
            throw new ConflitRessourceException("Un retour au fournisseur a été préparé sur le bon " + bon.getNumero()
                + " : il ne peut plus être corrigé");
        }
        Long ancienFournisseurId = bon.getFournisseur().getId();
        String ancienNumero = bon.getNumero();
        Map<Long, Apport> anciens = apportsParArticle(bon.getLignes());
        BigDecimal ancienTotal = anciens.values().stream().map(Apport::valeur).reduce(BigDecimal.ZERO, BigDecimal::add);

        Fournisseur fournisseur = trouverFournisseur(request.fournisseurId());
        ApprovisionnementMapper.mettreAJourApprovisionnement(bon, request, fournisseur);
        rattacherArticles(bon, request.lignes());
        Map<Long, Apport> nouveaux = apportsParArticle(bon.getLignes());
        BigDecimal nouveauTotal = nouveaux.values().stream().map(Apport::valeur).reduce(BigDecimal.ZERO, BigDecimal::add);

        TreeSet<Long> identifiants = new TreeSet<>(anciens.keySet());
        identifiants.addAll(nouveaux.keySet());
        Map<Long, Article> articles = verrouiller(new ArrayList<>(identifiants));

        // Tout vérifier avant de rien modifier : on ne retire pas du stock ce qui a déjà été vendu.
        for (Long articleId : identifiants) {
            Article article = articles.get(articleId);
            BigDecimal ancienne = anciens.getOrDefault(articleId, Apport.AUCUN).quantite();
            BigDecimal nouvelle = nouveaux.getOrDefault(articleId, Apport.AUCUN).quantite();
            if (article.getQuantiteStock().subtract(ancienne).add(nouvelle).signum() < 0) {
                throw new ConflitRessourceException("Article " + article.getReference() + " : il n'en reste que "
                    + Quantites.texte(article.getQuantiteStock()) + " en stock (le reste a été vendu ou sorti) ; la quantité "
                    + "reçue ne peut pas descendre sous " + Quantites.texte(ancienne.subtract(article.getQuantiteStock())));
            }
        }

        Utilisateur utilisateur = utilisateurRepository.findByUsername(username).orElse(null);
        List<String> changements = new ArrayList<>();
        for (Long articleId : identifiants) {
            Article article = articles.get(articleId);
            Apport ancien = anciens.getOrDefault(articleId, Apport.AUCUN);
            Apport nouveau = nouveaux.getOrDefault(articleId, Apport.AUCUN);
            if (ancien.memeQue(nouveau)) {
                continue;
            }
            BigDecimal stockAvant = article.getQuantiteStock();
            BigDecimal ancienCmp = article.getCmp();
            BigDecimal ancienPrix = article.getPrixAchat();
            // 1. Retirer l'ancien apport à son coût (si le reste du stock a été vendu, il n'y a plus rien à pondérer).
            BigDecimal stockSansAncien = stockAvant.subtract(ancien.quantite());
            BigDecimal cmp = ancien.quantite().signum() > 0 && stockSansAncien.signum() > 0
                ? CoutMoyenPondere.apresRetourFournisseur(stockAvant, ancienCmp, ancien.quantite(), ancien.cout())
                : ancienCmp;
            // 2. Ajouter l'apport corrigé.
            if (nouveau.quantite().signum() > 0) {
                cmp = CoutMoyenPondere.apresEntree(stockSansAncien.max(BigDecimal.ZERO), cmp, nouveau.quantite(), nouveau.cout());
                article.setPrixAchat(nouveau.cout());
            }
            BigDecimal stockApres = stockSansAncien.add(nouveau.quantite());
            article.setCmp(cmp);
            article.setQuantiteStock(stockApres);
            enregistrerHistoriquePrix(article, bon, utilisateur, ancienPrix, ancienCmp, cmp, stockAvant,
                article.getPrixAchat(), nouveau.quantite());

            BigDecimal ecart = nouveau.quantite().subtract(ancien.quantite());
            String commentaire = "Correction du bon " + bon.getNumero() + " : quantité " + Quantites.texte(ancien.quantite())
                + " → " + Quantites.texte(nouveau.quantite());
            if (ecart.signum() > 0) {
                creerMouvement(bon, article, TypeMouvementStock.ENTREE_APPROVISIONNEMENT, ecart, nouveau.cout(), stockAvant, stockApres,
                    commentaire);
            } else if (ecart.signum() < 0) {
                creerMouvement(bon, article, TypeMouvementStock.AJUSTEMENT_NEGATIF, ecart.negate(), ancien.cout(), stockAvant, stockApres,
                    commentaire);
            }
            changements.add(article.getReference() + " " + Quantites.texte(ancien.quantite()) + " à " + texte(ancien.cout())
                + " → " + Quantites.texte(nouveau.quantite()) + " à " + texte(nouveau.cout()));
        }
        for (LigneApprovisionnement ligne : bon.getLignes()) {
            appliquerPrixDeVente(articles.get(ligne.getArticle().getId()), ligne.getPrixVente(), bon, username);
        }
        articleRepository.saveAll(articles.values());
        approvisionnementRepository.saveAndFlush(bon);
        compteFournisseurService.corrigerDette(bon, ancienFournisseurId, username);
        journalActiviteService.enregistrer("MODIFICATION", "APPROVISIONNEMENT", bon.getId(),
            "Bon " + ancienNumero + (ancienNumero.equals(bon.getNumero()) ? "" : " (devenu " + bon.getNumero() + ")")
                + " corrigé après réception : total " + texte(ancienTotal) + " → " + texte(nouveauTotal)
                + (changements.isEmpty() ? "" : " ; " + String.join(" ; ", changements)), username);
        return ApprovisionnementMapper.toResponse(bon);
    }

    /** Le prix de revente saisi sur le bon devient le prix de vente de l'article (s'il change), et c'est noté au journal. */
    private void appliquerPrixDeVente(Article article, BigDecimal prixVente, Approvisionnement bon, String username) {
        if (prixVente == null || prixVente.compareTo(article.getPrix()) == 0) {
            return;
        }
        BigDecimal ancien = article.getPrix();
        article.setPrix(prixVente);
        journalActiviteService.enregistrer("MODIFICATION", "ARTICLE", article.getId(),
            "Article " + article.getReference() + " « " + article.getDesignation() + " » : prix de vente " + texte(ancien)
                + " → " + texte(prixVente) + " (bon " + bon.getNumero() + ")", username);
    }

    private static String texte(BigDecimal montant) {
        return Quantites.texte(montant.setScale(2, RoundingMode.HALF_UP));
    }

    /** Verrous des articles dans l'ordre des identifiants : deux opérations concurrentes ne peuvent pas s'interbloquer. */
    private Map<Long, Article> verrouiller(List<Long> identifiants) {
        Map<Long, Article> articles = new HashMap<>();
        identifiants.stream().distinct().sorted().forEach(articleId -> articles.put(articleId,
            articleRepository.findByIdForUpdate(articleId)
                .orElseThrow(() -> new RessourceIntrouvableException("Article introuvable: " + articleId))));
        return articles;
    }

    private void enregistrerHistoriquePrix(
        Article article,
        Approvisionnement approvisionnement,
        Utilisateur utilisateur,
        BigDecimal ancienPrix,
        BigDecimal ancienCmp,
        BigDecimal nouveauCmp,
        BigDecimal stockAvant,
        BigDecimal nouveauPrix,
        BigDecimal quantiteRecue
    ) {
        HistoriquePrixAchat historique = new HistoriquePrixAchat();
        historique.setArticle(article);
        historique.setDateEffet(java.time.LocalDateTime.now());
        historique.setAncienPrix(ancienPrix);
        historique.setNouveauPrix(nouveauPrix);
        historique.setAncienCmp(ancienCmp);
        historique.setNouveauCmp(nouveauCmp);
        historique.setStockAvant(stockAvant);
        historique.setQuantiteRecue(quantiteRecue);
        historique.setApprovisionnement(approvisionnement);
        historique.setUtilisateur(utilisateur);
        historiquePrixAchatRepository.save(historique);
    }

    private void creerMouvement(
        Approvisionnement approvisionnement,
        Article article,
        TypeMouvementStock type,
        BigDecimal quantite,
        BigDecimal coutUnitaire,
        BigDecimal stockAvant,
        BigDecimal stockApres,
        String commentaire
    ) {
        MouvementStock mouvement = new MouvementStock();
        mouvement.setType(type);
        mouvement.setQuantite(quantite);
        mouvement.setStockAvant(stockAvant);
        mouvement.setStockApres(stockApres);
        mouvement.setDateMouvement(java.time.LocalDateTime.now());
        mouvement.setCoutUnitaire(coutUnitaire);
        mouvement.setReferenceDocument(approvisionnement.getNumero());
        mouvement.setCommentaire(commentaire);
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
                "Seul un brouillon peut être supprimé ou validé");
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
