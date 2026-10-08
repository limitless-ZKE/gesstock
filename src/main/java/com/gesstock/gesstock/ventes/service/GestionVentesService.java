package com.gesstock.gesstock.ventes.service;

import com.gesstock.gesstock.entities.*;
import com.gesstock.gesstock.repositories.*;
import com.gesstock.gesstock.ventes.dto.*;
import com.gesstock.gesstock.common.exception.ConflitRessourceException;
import com.gesstock.gesstock.common.exception.RequeteInvalideException;
import com.gesstock.gesstock.common.exception.RessourceIntrouvableException;
import com.gesstock.gesstock.ventes.mapper.VentesMapper;
import com.gesstock.gesstock.promotions.service.PromotionService;
import com.gesstock.gesstock.caisse.dto.MouvementCaisseRequest;
import com.gesstock.gesstock.caisse.service.MouvementCaisseService;
import com.gesstock.gesstock.journal.service.JournalActiviteService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class GestionVentesService {

    private final ProformaRepository proformaRepository;
    private final VenteRepository venteRepository;
    private final ArticleRepository articleRepository;
    private final ClientRepository clientRepository;
    private final UniteVenteRepository uniteVenteRepository;
    private final SessionCaisseRepository sessionCaisseRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final MouvementStockRepository mouvementStockRepository;
    private final DetteClientRepository detteClientRepository;
    private final PromotionService promotionService;
    private final JournalActiviteService journalActiviteService;
    private final MouvementCaisseService mouvementCaisseService;

    public GestionVentesService(
        ProformaRepository proformaRepository,
        VenteRepository venteRepository,
        ArticleRepository articleRepository,
        ClientRepository clientRepository,
        UniteVenteRepository uniteVenteRepository,
        SessionCaisseRepository sessionCaisseRepository,
        UtilisateurRepository utilisateurRepository,
        MouvementStockRepository mouvementStockRepository,
        DetteClientRepository detteClientRepository,
        PromotionService promotionService,
        JournalActiviteService journalActiviteService,
        MouvementCaisseService mouvementCaisseService
    ) {
        this.mouvementCaisseService = mouvementCaisseService;
        this.proformaRepository = proformaRepository;
        this.venteRepository = venteRepository;
        this.articleRepository = articleRepository;
        this.clientRepository = clientRepository;
        this.uniteVenteRepository = uniteVenteRepository;
        this.sessionCaisseRepository = sessionCaisseRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.mouvementStockRepository = mouvementStockRepository;
        this.detteClientRepository = detteClientRepository;
        this.promotionService = promotionService;
        this.journalActiviteService = journalActiviteService;
    }

    @Transactional(readOnly = true)
    public List<ProformaResponse> listerProformas() {
        return proformaRepository.findAll().stream().map(VentesMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ProformaResponse obtenirProforma(Long id) {
        return VentesMapper.toResponse(trouverProforma(id));
    }

    public ProformaResponse creerProforma(ProformaRequest request, String username) {
        verifierNumeroProformaDisponible(request.numero(), null);
        Proforma proforma = new Proforma();
        proforma.setNumero(request.numero().trim());
        proforma.setDateEmission(request.dateEmission() == null ? LocalDate.now() : request.dateEmission());
        proforma.setDateExpiration(request.dateExpiration());
        proforma.setStatut(StatutProforma.BROUILLON);
        proforma.setCreePar(trouverUtilisateurActif(username));
        proforma.setClient(request.clientId() == null ? null : trouverClient(request.clientId()));
        remplacerLignesProforma(proforma, request.lignes());
        validerPeriodeProforma(proforma);
        Proforma saved = proformaRepository.save(proforma);
        journalActiviteService.enregistrer("CREATION", "PROFORMA", saved.getId(),
            "Création du proforma " + saved.getNumero(), username);
        return VentesMapper.toResponse(saved);
    }

    public ProformaResponse modifierProforma(Long id, ProformaRequest request) {
        Proforma proforma = trouverProforma(id);
        if (proforma.getStatut() != StatutProforma.BROUILLON) {
            throw new ConflitRessourceException("Seul un proforma brouillon peut être modifié");
        }
        verifierNumeroProformaDisponible(request.numero(), id);
        proforma.setNumero(request.numero().trim());
        proforma.setDateEmission(request.dateEmission() == null ? proforma.getDateEmission() : request.dateEmission());
        proforma.setDateExpiration(request.dateExpiration());
        proforma.setClient(request.clientId() == null ? null : trouverClient(request.clientId()));
        remplacerLignesProforma(proforma, request.lignes());
        validerPeriodeProforma(proforma);
        return VentesMapper.toResponse(proformaRepository.save(proforma));
    }

    public ProformaResponse modifierStatutProforma(Long id, StatutProforma nouveauStatut, String username) {
        Proforma proforma = proformaRepository.findByIdForUpdate(id)
            .orElseThrow(() -> new RessourceIntrouvableException("Proforma introuvable: " + id));
        boolean transitionAutorisee = switch (proforma.getStatut()) {
            case BROUILLON -> nouveauStatut == StatutProforma.ENVOYEE;
            case ENVOYEE -> nouveauStatut == StatutProforma.ACCEPTEE
                || nouveauStatut == StatutProforma.REFUSEE;
            default -> false;
        };
        if (!transitionAutorisee) {
            throw new ConflitRessourceException(
                "Transition de statut invalide pour le proforma " + proforma.getNumero());
        }
        if (nouveauStatut == StatutProforma.ACCEPTEE
            && proforma.getDateExpiration() != null
            && proforma.getDateExpiration().isBefore(LocalDate.now())) {
            throw new ConflitRessourceException("La date de validité du proforma est dépassée");
        }
        proforma.setStatut(nouveauStatut);
        Proforma saved = proformaRepository.save(proforma);
        journalActiviteService.enregistrer("CHANGEMENT_STATUT", "PROFORMA", saved.getId(),
            "Statut du proforma " + saved.getNumero() + " modifié en " + nouveauStatut, username);
        return VentesMapper.toResponse(saved);
    }

    public void supprimerProforma(Long id) {
        Proforma proforma = trouverProforma(id);
        if (proforma.getStatut() == StatutProforma.CONVERTIE || proforma.getVente() != null) {
            throw new ConflitRessourceException("Un proforma déjà converti ne peut pas être supprimé");
        }
        proformaRepository.delete(proforma);
    }

    @Transactional(readOnly = true)
    /** Ventes validées ou annulées (celles qui ont un reçu, pas les brouillons) le jour donné, les plus récentes d'abord ; toutes si {@code jour} est nul. */
    /** Ventes validées ou annulées d'une journée : le volume est borné par l'activité d'un jour. */
    public List<VenteResponse> listerVentes(LocalDate jour) {
        return venteRepository.findByStatutInAndDateVenteGreaterThanEqualAndDateVenteLessThanOrderByDateVenteDesc(
                List.of(StatutVente.VALIDEE, StatutVente.ANNULEE), jour.atStartOfDay(), jour.plusDays(1).atStartOfDay())
            .stream().map(VentesMapper::toResponse).toList();
    }

    public static final int TAILLE_MAX_PAGE = 200;

    /**
     * Historique paginé, les ventes les plus récentes d'abord, éventuellement limité à un client. La taille est bornée :
     * une liste de toutes les ventes grossit sans limite (des dizaines de milliers par an) et gèlerait l'écran.
     */
    @Transactional(readOnly = true)
    public PageVentesResponse listerVentesPaginees(Long clientId, int page, int taille) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(taille, 1), TAILLE_MAX_PAGE));
        Page<Long> identifiants = clientId == null
            ? venteRepository.identifiantsRecents(pageable)
            : venteRepository.identifiantsDuClient(clientId, pageable);
        List<VenteResponse> ventes = identifiants.isEmpty() ? List.of()
            : venteRepository.chargerAvecDetails(identifiants.getContent()).stream().map(VentesMapper::toResponse).toList();
        return new PageVentesResponse(ventes, identifiants.getTotalElements());
    }

    @Transactional(readOnly = true)
    public VenteResponse obtenirVente(Long id) {
        return VentesMapper.toResponse(trouverVente(id));
    }

    public VenteResponse creerVenteDirecte(VenteDirecteRequest request, String username) {
        if (request.aCredit() && request.clientId() == null) {
            throw new ConflitRessourceException("Une vente à crédit doit être rattachée à un client");
        }
        Vente vente = new Vente();
        vente.setStatut(StatutVente.BROUILLON);
        vente.setVenteACredit(request.aCredit());
        vente.setTauxTva(request.tauxTva() == null ? BigDecimal.ZERO : request.tauxTva());
        vente.setModeReglement(request.modeReglement());
        vente.setMontantRecu(request.montantRecu());
        vente.setVendeur(trouverUtilisateurActif(username));
        vente.setClient(request.clientId() == null ? null : trouverClient(request.clientId()));
        vente.setLignesVente(new ArrayList<>());
        for (LigneDocumentRequest requestLigne : request.lignes()) {
            vente.getLignesVente().add(creerLigneVente(vente, requestLigne));
        }
        BigDecimal montantHT = calculerTotalVente(vente.getLignesVente());
        BigDecimal montantTva = montantHT.multiply(vente.getTauxTva())
            .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        vente.setMontantHT(montantHT);
        vente.setMontantTva(montantTva);
        vente.setMontantTotal(montantHT.add(montantTva));
        validerMontantRecu(vente);
        return VentesMapper.toResponse(venteRepository.save(vente));
    }

    public VenteResponse validerVente(Long id, String username) {
        Vente vente = venteRepository.findByIdForUpdate(id)
            .orElseThrow(() -> new RessourceIntrouvableException("Vente introuvable: " + id));
        if (vente.getStatut() != StatutVente.BROUILLON) {
            throw new ConflitRessourceException("Seule une vente brouillon peut être validée");
        }
        validerEtDecrementerStock(vente, username);
        return VentesMapper.toResponse(venteRepository.save(vente));
    }

    /**
     * Annule une vente à crédit validée : le stock vendu est remis en rayon (mouvement RETOUR_CLIENT)
     * et la dette liée est annulée.
     *
     * <p>Si le client a déjà versé de l'argent (dette partiellement ou totalement réglée), l'annulation
     * exige qu'il rapporte les articles en l'état ({@code articlesRetournesEnEtat}) et ses versements lui
     * sont remboursés : la part payée en espèces sort de la caisse du jour (une journée ouverte et un
     * solde suffisant sont requis), les autres modes sont remboursés par le même moyen et tracés au journal.
     */
    public AnnulationVenteResponse annulerVenteACredit(
        Long id, String motif, boolean articlesRetournesEnEtat, String username
    ) {
        Vente vente = venteRepository.findByIdForUpdate(id)
            .orElseThrow(() -> new RessourceIntrouvableException("Vente introuvable: " + id));
        if (vente.getStatut() != StatutVente.VALIDEE) {
            throw new ConflitRessourceException("Seule une vente validée peut être annulée");
        }
        if (!vente.isVenteACredit() || vente.getDetteClient() == null) {
            throw new ConflitRessourceException("Seule une vente à crédit peut être annulée");
        }
        DetteClient dette = vente.getDetteClient();
        BigDecimal remboursementEspeces = dette.getReglements().stream()
            .filter(reglement -> reglement.getModeReglement() == ModeReglement.ESPECES)
            .map(ReglementClient::getMontant)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal montantRembourse = dette.getReglements().stream()
            .map(ReglementClient::getMontant)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal remboursementAutres = montantRembourse.subtract(remboursementEspeces);
        if (montantRembourse.signum() > 0 && !articlesRetournesEnEtat) {
            throw new ConflitRessourceException(
                "Le client a déjà versé " + montantRembourse.toPlainString()
                    + " : la vente ne peut être annulée et remboursée que s'il rapporte les articles en l'état");
        }
        Utilisateur utilisateur = trouverUtilisateurActif(username);

        // Remboursement en premier : la part en espèces sort de la caisse du jour. Il est refusé, avant
        // toute modification du stock ou de la dette, si aucune journée n'est ouverte ou si la caisse
        // ne contient pas assez d'espèces.
        if (remboursementEspeces.signum() > 0) {
            String beneficiaire = vente.getClient() == null ? "Client" : vente.getClient().getNom();
            mouvementCaisseService.enregistrer(new MouvementCaisseRequest(
                "Remboursement client - annulation de la vente " + vente.getCode(),
                beneficiaire.length() > 120 ? beneficiaire.substring(0, 120) : beneficiaire,
                remboursementEspeces), username);
        }

        Map<Long, Integer> quantitesBase = new LinkedHashMap<>();
        for (LigneVente ligne : vente.getLignesVente()) {
            quantitesBase.merge(ligne.getArticle().getId(), quantiteEnUniteDeStock(ligne), Math::addExact);
        }
        Map<Long, Article> articles = new HashMap<>();
        quantitesBase.keySet().stream().sorted().forEach(articleId -> articles.put(articleId,
            articleRepository.findByIdForUpdate(articleId)
                .orElseThrow(() -> new RessourceIntrouvableException("Article introuvable: " + articleId))));

        Map<Long, Integer> stockCourant = new HashMap<>();
        articles.forEach((articleId, article) -> stockCourant.put(articleId, article.getQuantiteStock()));
        for (LigneVente ligne : vente.getLignesVente()) {
            Article article = articles.get(ligne.getArticle().getId());
            int quantite = quantiteEnUniteDeStock(ligne);
            int avant = stockCourant.get(article.getId());
            int apres = avant + quantite;
            stockCourant.put(article.getId(), apres);
            article.setQuantiteStock(apres);

            MouvementStock mouvement = new MouvementStock();
            mouvement.setType(TypeMouvementStock.RETOUR_CLIENT);
            mouvement.setQuantite(quantite);
            mouvement.setStockAvant(avant);
            mouvement.setStockApres(apres);
            mouvement.setDateMouvement(LocalDateTime.now());
            mouvement.setReferenceDocument(vente.getCode());
            mouvement.setCommentaire("Annulation de la vente : " + motif.trim());
            mouvement.setArticle(article);
            mouvement.setEffectuePar(utilisateur);
            mouvement.setSessionCaisse(vente.getSessionCaisse());
            mouvement.setVente(vente);
            mouvementStockRepository.save(mouvement);
        }
        articleRepository.saveAll(articles.values());

        dette.setStatut(StatutDette.ANNULEE);
        detteClientRepository.save(dette);
        vente.setStatut(StatutVente.ANNULEE);
        Vente saved = venteRepository.save(vente);

        String detailRemboursement = montantRembourse.signum() == 0
            ? "aucun versement à rembourser"
            : "versements remboursés : " + montantRembourse.toPlainString()
                + " (espèces " + remboursementEspeces.toPlainString()
                + ", autres modes " + remboursementAutres.toPlainString() + "), articles rapportés en l'état";
        journalActiviteService.enregistrer("ANNULATION", "VENTE", saved.getId(),
            "Annulation de la vente à crédit " + saved.getCode() + " (" + motif.trim()
                + "), stock remis en rayon, dette annulée, " + detailRemboursement,
            username);
        return new AnnulationVenteResponse(
            VentesMapper.toResponse(saved), montantRembourse, remboursementEspeces, remboursementAutres);
    }

    public VenteResponse convertirProformaEnVente(
        Long proformaId, boolean aCredit, ModeReglement modeReglement, BigDecimal montantRecu, String username
    ) {
        Proforma proforma = proformaRepository.findByIdForUpdate(proformaId)
            .orElseThrow(() -> new RessourceIntrouvableException("Proforma introuvable: " + proformaId));
        if (proforma.getStatut() != StatutProforma.ACCEPTEE || proforma.getVente() != null) {
            throw new ConflitRessourceException("Seul un proforma accepté peut être converti en vente");
        }
        if (proforma.getDateExpiration() != null && proforma.getDateExpiration().isBefore(LocalDate.now())) {
            throw new ConflitRessourceException("La date de validité du proforma est dépassée");
        }

        Utilisateur utilisateur = trouverUtilisateurActif(username);
        if (aCredit && proforma.getClient() == null) {
            throw new ConflitRessourceException("La conversion à crédit exige un client associé au proforma");
        }
        Vente vente = new Vente();
        vente.setStatut(StatutVente.BROUILLON);
        vente.setVenteACredit(aCredit);
        vente.setClient(proforma.getClient());
        vente.setVendeur(utilisateur);
        vente.setProforma(proforma);
        vente.setLignesVente(new ArrayList<>());
        for (LigneProforma ligneProforma : proforma.getLignes()) {
            LigneVente ligne = new LigneVente();
            ligne.setVente(vente);
            ligne.setArticle(ligneProforma.getArticle());
            ligne.setUniteVente(ligneProforma.getUniteVente());
            ligne.setQuantite(ligneProforma.getQuantite());
            ligne.setPrixUnitaire(ligneProforma.getPrixUnitaire());
            ligne.setPrixCatalogue(ligneProforma.getPrixCatalogue());
            ligne.setPromotion(ligneProforma.getPromotion());
            ligne.setRemiseUnitaire(ligneProforma.getRemiseUnitaire());
            vente.getLignesVente().add(ligne);
        }
        vente.setMontantTotal(proforma.getMontantTotal());
        // Un proforma n'a pas de TVA : HT = total et TVA = 0, posés explicitement (sinon le reçu afficherait un HT à 0).
        vente.setMontantHT(proforma.getMontantTotal());
        vente.setMontantTva(BigDecimal.ZERO);
        vente.setTauxTva(BigDecimal.ZERO);
        // Au comptant, le règlement est obligatoire : sans lui, le cash encaissé serait invisible dans la caisse.
        vente.setModeReglement(aCredit ? null : modeReglement);
        vente.setMontantRecu(aCredit ? null : montantRecu);
        validerMontantRecu(vente);
        vente = venteRepository.save(vente);
        proforma.setVente(vente);

        validerEtDecrementerStock(vente, username);
        vente.setStatut(StatutVente.VALIDEE);
        proforma.setStatut(StatutProforma.CONVERTIE);
        proformaRepository.save(proforma);
        Vente saved = venteRepository.save(vente);
        journalActiviteService.enregistrer("VALIDATION", "VENTE", saved.getId(),
            "Vente " + saved.getCode() + " issue du proforma " + proforma.getNumero(), username);
        return VentesMapper.toResponse(saved);
    }

    private void validerEtDecrementerStock(Vente vente, String username) {
        LocalDate aujourdHui = LocalDate.now();
        SessionCaisse session = sessionCaisseRepository.findByJourneeAndStatutForUpdate(
                aujourdHui, StatutSessionCaisse.OUVERTE)
            .orElseThrow(() -> new ConflitRessourceException(
                sessionCaisseRepository.findFirstByStatutOrderByOuverteLeDesc(StatutSessionCaisse.OUVERTE)
                    .map(restee -> "La journée de caisse du " + restee.getJournee() + " n'a pas été clôturée : clôturez-la,"
                        + " puis ouvrez celle d'aujourd'hui avant de valider une vente")
                    .orElse("La journée de caisse doit être ouverte avant de valider une vente")));
        Utilisateur vendeur = trouverUtilisateurActif(username);
        if (vente.getLignesVente() == null || vente.getLignesVente().isEmpty()) {
            throw new ConflitRessourceException("Une vente sans ligne ne peut pas être validée");
        }

        Map<Long, Integer> quantitesBase = new LinkedHashMap<>();
        for (LigneVente ligne : vente.getLignesVente()) {
            long articleId = ligne.getArticle().getId();
            int quantiteBase = quantiteEnUniteDeStock(ligne);
            try {
                quantitesBase.merge(articleId, quantiteBase, Math::addExact);
            } catch (ArithmeticException exception) {
                throw new ConflitRessourceException("Quantité agrégée trop importante pour l'article " + articleId);
            }
        }

        Map<Long, Article> articles = new HashMap<>();
        quantitesBase.keySet().stream().sorted().forEach(articleId -> {
            Article article = articleRepository.findByIdForUpdate(articleId)
                .orElseThrow(() -> new RessourceIntrouvableException("Article introuvable: " + articleId));
            int necessaire = quantitesBase.get(articleId);
            if (article.getQuantiteStock() < necessaire) {
                throw new ConflitRessourceException("Stock insuffisant pour l'article " + article.getReference()
                    + ": demandé " + necessaire + ", disponible " + article.getQuantiteStock());
            }
            articles.put(articleId, article);
        });

        vente.setSessionCaisse(session);
        vente.setVendeur(vendeur);
        vente.setDateVente(LocalDateTime.now());
        vente.setStatut(StatutVente.VALIDEE);
        venteRepository.saveAndFlush(vente);

        if (vente.isVenteACredit()) {
            creerDetteVente(vente);
        }

        Map<Long, Integer> stockCourant = new HashMap<>();
        articles.forEach((id, article) -> stockCourant.put(id, article.getQuantiteStock()));
        for (LigneVente ligne : vente.getLignesVente()) {
            Article article = articles.get(ligne.getArticle().getId());
            int quantite = quantiteEnUniteDeStock(ligne);
            int avant = stockCourant.get(article.getId());
            int apres = avant - quantite;
            stockCourant.put(article.getId(), apres);
            article.setQuantiteStock(apres);
            creerMouvementStock(vente, ligne, article, quantite, avant, apres, vendeur, session);
        }
        articleRepository.saveAll(articles.values());
        journalActiviteService.enregistrer("VALIDATION", "VENTE", vente.getId(),
            "Validation de la vente " + vente.getCode() + ", total " + vente.getMontantTotal(), username);
    }

    private void creerDetteVente(Vente vente) {
        if (vente.getClient() == null) {
            throw new ConflitRessourceException("Une vente à crédit doit être rattachée à un client");
        }
        BigDecimal montant = vente.getMontantTotal();
        if (montant.signum() <= 0) {
            throw new ConflitRessourceException("Le montant d'une vente à crédit doit être positif");
        }
        DetteClient dette = new DetteClient();
        dette.setDateCreation(LocalDate.now());
        dette.setMontantInitial(montant);
        dette.setMontantRegle(BigDecimal.ZERO);
        dette.setStatut(StatutDette.EN_COURS);
        dette.setClient(vente.getClient());
        dette.setVente(vente);
        vente.setDetteClient(dette);
        detteClientRepository.save(dette);
    }

    private int quantiteEnUniteDeStock(LigneVente ligne) {
        BigDecimal facteur = ligne.getUniteVente() == null
            ? BigDecimal.ONE
            : ligne.getUniteVente().getFacteurConversion();
        try {
            return facteur.multiply(BigDecimal.valueOf(ligne.getQuantite()))
                .setScale(0, RoundingMode.UNNECESSARY)
                .intValueExact();
        } catch (ArithmeticException exception) {
            throw new ConflitRessourceException("La conversion en unité de stock doit donner une quantité entière");
        }
    }

    private void creerMouvementStock(
        Vente vente,
        LigneVente ligne,
        Article article,
        int quantite,
        int avant,
        int apres,
        Utilisateur vendeur,
        SessionCaisse session
    ) {
        MouvementStock mouvement = new MouvementStock();
        mouvement.setType(TypeMouvementStock.SORTIE_VENTE);
        mouvement.setQuantite(quantite);
        mouvement.setStockAvant(avant);
        mouvement.setStockApres(apres);
        mouvement.setDateMouvement(LocalDateTime.now());
        mouvement.setReferenceDocument(vente.getCode());
        mouvement.setCommentaire(ligne.getUniteVente() == null ? null : "Unité: " + ligne.getUniteVente().getCode());
        mouvement.setArticle(article);
        mouvement.setEffectuePar(vendeur);
        mouvement.setSessionCaisse(session);
        mouvement.setVente(vente);
        mouvementStockRepository.save(mouvement);
    }

    private BigDecimal prixCatalogue(Article article, UniteVente unite) {
        return unite != null && unite.getPrixVente() != null ? unite.getPrixVente() : article.getPrix();
    }

    /** Prix négocié demandé, ou prix catalogue à défaut ; le prix négocié ne peut pas dépasser le catalogue. */
    private BigDecimal prixApplique(BigDecimal demande, BigDecimal catalogue, Article article) {
        if (demande == null) {
            return catalogue;
        }
        if (demande.compareTo(catalogue) > 0) {
            throw new RequeteInvalideException("Le prix négocié de l'article " + article.getReference()
                + " (" + demande.toPlainString() + ") ne peut pas dépasser son prix catalogue ("
                + catalogue.toPlainString() + ")");
        }
        return demande;
    }

    private LigneVente creerLigneVente(Vente vente, LigneDocumentRequest request) {
        Article article = trouverArticle(request.articleId());
        UniteVente unite = request.uniteVenteId() == null ? null : trouverUniteVente(request.uniteVenteId(), article);
        BigDecimal prixCatalogue = prixCatalogue(article, unite);
        BigDecimal prix = prixApplique(request.prixUnitaire(), prixCatalogue, article);
        LigneVente ligne = new LigneVente();
        ligne.setPrixCatalogue(prixCatalogue);
        ligne.setVente(vente);
        ligne.setArticle(article);
        ligne.setUniteVente(unite);
        ligne.setQuantite(request.quantite());
        ligne.setPrixUnitaire(prix);
        Promotion promotion = promotionService.promotionChoisie(article, request.promotionId(), LocalDateTime.now());
        ligne.setPromotion(promotion);
        ligne.setRemiseUnitaire(promotionService.montantRemise(promotion, prix));
        return ligne;
    }

    private void remplacerLignesProforma(Proforma proforma, List<LigneDocumentRequest> demandes) {
        proforma.getLignes().clear();
        BigDecimal total = BigDecimal.ZERO;
        for (LigneDocumentRequest demande : demandes) {
            Article article = trouverArticle(demande.articleId());
            UniteVente unite = demande.uniteVenteId() == null
                ? null
                : trouverUniteVente(demande.uniteVenteId(), article);
            BigDecimal prixCatalogue = prixCatalogue(article, unite);
            BigDecimal prix = prixApplique(demande.prixUnitaire(), prixCatalogue, article);
            Promotion promotion = promotionService.promotionChoisie(
                article, demande.promotionId(), proforma.getDateEmission().atStartOfDay());
            BigDecimal remiseUnitaire = promotionService.montantRemise(promotion, prix);
            BigDecimal montantNet = prix.subtract(remiseUnitaire).multiply(BigDecimal.valueOf(demande.quantite()));
            LigneProforma ligne = new LigneProforma();
            ligne.setProforma(proforma);
            ligne.setArticle(article);
            ligne.setUniteVente(unite);
            ligne.setQuantite(demande.quantite());
            ligne.setPrixUnitaire(prix);
            ligne.setPrixCatalogue(prixCatalogue);
            ligne.setMontantLigne(montantNet);
            ligne.setPromotion(promotion);
            ligne.setRemiseUnitaire(remiseUnitaire);
            proforma.getLignes().add(ligne);
            total = total.add(montantNet);
        }
        proforma.setMontantTotal(total);
    }

    private BigDecimal calculerTotalVente(List<LigneVente> lignes) {
        return lignes.stream()
            .map(ligne -> ligne.getPrixUnitaire()
                .subtract(ligne.getRemiseUnitaire() == null ? BigDecimal.ZERO : ligne.getRemiseUnitaire())
                .multiply(BigDecimal.valueOf(ligne.getQuantite())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void validerMontantRecu(Vente vente) {
        if (vente.getModeReglement() == null) {
            if (vente.getMontantRecu() != null) {
                throw new ConflitRessourceException("Un mode de règlement est requis pour enregistrer un montant reçu");
            }
            if (!vente.isVenteACredit()) {
                throw new ConflitRessourceException("Une vente au comptant exige un mode de règlement et le montant reçu");
            }
            return;
        }
        if (vente.isVenteACredit()) {
            throw new ConflitRessourceException(
                "Une vente à crédit doit être réglée via le suivi des dettes clients");
        }
        if (vente.getMontantRecu() == null || vente.getMontantRecu().compareTo(
            vente.getMontantTotal()) < 0) {
            throw new ConflitRessourceException("Le montant reçu doit couvrir le total TTC de la vente");
        }
        if (vente.getModeReglement() != ModeReglement.ESPECES
            && vente.getMontantRecu().compareTo(vente.getMontantTotal()) != 0) {
            throw new ConflitRessourceException(
                "Un règlement sans espèces doit correspondre exactement au total TTC");
        }
    }

    private void validerPeriodeProforma(Proforma proforma) {
        if (proforma.getDateExpiration() != null
            && proforma.getDateExpiration().isBefore(proforma.getDateEmission())) {
            throw new ConflitRessourceException("La date d'expiration précède la date d'émission");
        }
    }

    private void verifierNumeroProformaDisponible(String numero, Long idIgnore) {
        proformaRepository.findByNumeroIgnoreCase(numero.trim()).ifPresent(existing -> {
            if (!existing.getId().equals(idIgnore)) {
                throw new ConflitRessourceException("Un proforma portant ce numéro existe déjà");
            }
        });
    }

    private UniteVente trouverUniteVente(Long id, Article article) {
        UniteVente unite = uniteVenteRepository.findById(id)
            .orElseThrow(() -> new RessourceIntrouvableException("Unité de vente introuvable: " + id));
        if (!unite.isActive() || !unite.getArticle().getId().equals(article.getId())) {
            throw new ConflitRessourceException("L'unité de vente ne correspond pas à l'article ou est inactive");
        }
        return unite;
    }

    private Article trouverArticle(Long id) {
        return articleRepository.findById(id)
            .orElseThrow(() -> new RessourceIntrouvableException("Article introuvable: " + id));
    }

    private Client trouverClient(Long id) {
        return clientRepository.findById(id)
            .orElseThrow(() -> new RessourceIntrouvableException("Client introuvable: " + id));
    }

    private Utilisateur trouverUtilisateurActif(String username) {
        Utilisateur utilisateur = utilisateurRepository.findByUsername(username)
            .orElseThrow(() -> new RessourceIntrouvableException("Utilisateur connecté introuvable"));
        if (!utilisateur.isActif()) {
            throw new ConflitRessourceException("Un utilisateur désactivé ne peut pas enregistrer une vente");
        }
        return utilisateur;
    }

    private Proforma trouverProforma(Long id) {
        return proformaRepository.findById(id)
            .orElseThrow(() -> new RessourceIntrouvableException("Proforma introuvable: " + id));
    }

    private Vente trouverVente(Long id) {
        return venteRepository.findById(id)
            .orElseThrow(() -> new RessourceIntrouvableException("Vente introuvable: " + id));
    }
}
