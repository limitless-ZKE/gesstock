package com.gesstock.gesstock.ventes.service;

import com.gesstock.gesstock.entities.*;
import com.gesstock.gesstock.repositories.*;
import com.gesstock.gesstock.ventes.dto.*;
import com.gesstock.gesstock.common.exception.ConflitRessourceException;
import com.gesstock.gesstock.common.exception.PrixSousLeCoutException;
import com.gesstock.gesstock.common.exception.RequeteInvalideException;
import com.gesstock.gesstock.common.exception.RessourceIntrouvableException;
import com.gesstock.gesstock.stock.CoutMoyenPondere;
import com.gesstock.gesstock.stock.Quantites;
import com.gesstock.gesstock.ventes.fiscal.CalculTva;
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
    private final CalculTva calculTva;

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
        MouvementCaisseService mouvementCaisseService,
        CalculTva calculTva
    ) {
        this.calculTva = calculTva;
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
        Utilisateur createur = trouverUtilisateurActif(username);
        proforma.setCreePar(createur);
        proforma.setClient(request.clientId() == null ? null : trouverClient(request.clientId()));
        remplacerLignesProforma(proforma, request.lignes());
        validerPeriodeProforma(proforma);
        Proforma saved = proformaRepository.save(proforma);
        journalActiviteService.enregistrer("CREATION", "PROFORMA", saved.getId(),
            "Création du proforma " + saved.getNumero(), username);
        return VentesMapper.toResponse(saved);
    }

    public ProformaResponse modifierProforma(Long id, ProformaRequest request, String username) {
        Proforma proforma = trouverProforma(id);
        if (proforma.getStatut() != StatutProforma.BROUILLON) {
            throw new ConflitRessourceException("Seul un proforma brouillon peut être modifié");
        }
        verifierNumeroProformaDisponible(request.numero(), id);
        proforma.setNumero(request.numero().trim());
        proforma.setDateEmission(request.dateEmission() == null ? proforma.getDateEmission() : request.dateEmission());
        proforma.setDateExpiration(request.dateExpiration());
        proforma.setClient(request.clientId() == null ? null : trouverClient(request.clientId()));
        trouverUtilisateurActif(username);
        remplacerLignesProforma(proforma, request.lignes());
        validerPeriodeProforma(proforma);
        Proforma saved = proformaRepository.save(proforma);
        return VentesMapper.toResponse(saved);
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
        // Le taux de TVA est imposé par le serveur : une caisse qui enverrait un autre taux est refusée plutôt qu'obéie.
        if (request.tauxTva() != null && request.tauxTva().compareTo(calculTva.taux()) != 0) {
            throw new RequeteInvalideException("Le taux de TVA est fixé par le serveur ("
                + calculTva.taux().stripTrailingZeros().toPlainString() + " %) et ne se saisit pas à la caisse");
        }
        vente.setTauxTva(calculTva.taux());
        vente.setModeReglement(request.modeReglement());
        vente.setMontantRecu(request.montantRecu());
        Utilisateur vendeur = trouverUtilisateurActif(username);
        vente.setVendeur(vendeur);
        vente.setClient(request.clientId() == null ? null : trouverClient(request.clientId()));
        vente.setLignesVente(new ArrayList<>());
        // Le vendeur fixe librement le prix négocié, quel que soit le taux de remise (décision du client, novembre 2026) :
        // aucun accord n'est demandé. En revanche la VALIDATION refuse un prix négocié sous le prix d'achat : le coût moyen
        // n'est connu qu'à ce moment-là (une entrée en stock peut le changer entre le brouillon et la validation).
        for (LigneDocumentRequest requestLigne : request.lignes()) {
            vente.getLignesVente().add(creerLigneVente(vente, requestLigne));
        }
        CalculTva.Montants montants = calculTva.decomposer(calculerTotalVente(vente.getLignesVente()));
        vente.setMontantHT(montants.montantHT());
        vente.setMontantTva(montants.montantTva());
        vente.setMontantTotal(montants.montantTotal());
        validerMontantRecu(vente);
        Vente saved = venteRepository.save(vente);
        return VentesMapper.toResponse(saved);
    }

    /**
     * Valide la vente. Refusée (409, rien n'est modifié) si un prix négocié place une ligne sous le prix d'achat ; la réponse
     * signale encore les lignes vendues sous le prix d'achat sans négociation (promotion), qui, elles, passent.
     */
    public VenteResponse validerVente(Long id, String username) {
        Vente vente = venteRepository.findByIdForUpdate(id)
            .orElseThrow(() -> new RessourceIntrouvableException("Vente introuvable: " + id));
        if (vente.getStatut() != StatutVente.BROUILLON) {
            throw new ConflitRessourceException("Seule une vente brouillon peut être validée");
        }
        List<String> avertissements = validerEtDecrementerStock(vente, username);
        return VentesMapper.toResponse(venteRepository.save(vente), avertissements);
    }

    /** Annule une vente validée, à crédit ou au comptant : chaque cas a ses propres conditions. */
    public AnnulationVenteResponse annulerVente(Long id, String motif, boolean articlesRetournesEnEtat, String username) {
        Vente vente = venteRepository.findById(id)
            .orElseThrow(() -> new RessourceIntrouvableException("Vente introuvable: " + id));
        return vente.isVenteACredit()
            ? annulerVenteACredit(id, motif, articlesRetournesEnEtat, username)
            : annulerVenteAuComptant(id, motif, articlesRetournesEnEtat, username);
    }

    /**
     * Annule une vente au comptant : le client rapporte les articles en l'état et est remboursé.
     *
     * <p>Conditions : vente validée, articles rapportés en l'état, et journée de caisse de la vente encore ouverte (après la
     * clôture le comptage est arrêté : on ne rouvre pas une journée). Un remboursement en espèces exige que la caisse en
     * contienne assez. Le stock rentre au coût de la vente. Le remboursement en espèces n'est pas une « sortie de caisse »
     * séparée : la vente annulée quitte les encaissements, ce qui fait baisser le solde théorique du même montant.
     */
    private AnnulationVenteResponse annulerVenteAuComptant(
        Long id, String motif, boolean articlesRetournesEnEtat, String username
    ) {
        Vente vente = venteRepository.findByIdForUpdate(id)
            .orElseThrow(() -> new RessourceIntrouvableException("Vente introuvable: " + id));
        if (vente.getStatut() != StatutVente.VALIDEE) {
            throw new ConflitRessourceException("Seule une vente validée peut être annulée");
        }
        if (!articlesRetournesEnEtat) {
            throw new ConflitRessourceException("Une vente au comptant ne peut être annulée et remboursée que si le client "
                + "rapporte les articles en l'état");
        }
        SessionCaisse session = sessionCaisseRepository.findByJourneeAndStatutForUpdate(
                LocalDate.now(), StatutSessionCaisse.OUVERTE)
            .filter(ouverte -> vente.getSessionCaisse() != null && ouverte.getId().equals(vente.getSessionCaisse().getId()))
            .orElseThrow(() -> new ConflitRessourceException("La journée de caisse de cette vente est clôturée : une vente au "
                + "comptant ne peut être annulée que pendant sa journée de caisse"));
        BigDecimal montant = vente.getMontantTotal();
        boolean especes = vente.getModeReglement() == ModeReglement.ESPECES;
        if (especes) {
            BigDecimal disponible = mouvementCaisseService.soldeEspecesDisponible(session);
            if (montant.compareTo(disponible) > 0) {
                throw new ConflitRessourceException("La caisse ne contient pas assez d'espèces pour rembourser "
                    + montant.toPlainString() + " (disponible : " + disponible.toPlainString() + ")");
            }
        }
        Utilisateur utilisateur = trouverUtilisateurActif(username);

        remettreEnRayon(vente, motif, utilisateur);
        vente.setStatut(StatutVente.ANNULEE);
        Vente saved = venteRepository.save(vente);

        String modeRemboursement = especes ? "espèces, sorties de la caisse du jour"
            : "par le même moyen de paiement (" + (vente.getModeReglement() == null ? "inconnu" : vente.getModeReglement()) + ")";
        journalActiviteService.enregistrer("ANNULATION", "VENTE", saved.getId(),
            "Annulation de la vente au comptant " + saved.getCode() + " (" + motif.trim() + "), articles rapportés en l'état, "
                + "stock remis en rayon, remboursement de " + montant.toPlainString() + " : " + modeRemboursement, username);
        return new AnnulationVenteResponse(VentesMapper.toResponse(saved), montant,
            especes ? montant : BigDecimal.ZERO, especes ? BigDecimal.ZERO : montant);
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
                remboursementEspeces), username, OrigineMouvementCaisse.REMBOURSEMENT_CLIENT);
        }

        remettreEnRayon(vente, motif, utilisateur);

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

    /**
     * Remet en rayon les articles d'une vente annulée (mouvement RETOUR_CLIENT). Le stock rentre au coût MÉMORISÉ à la
     * vente, pas au coût du jour : le coût moyen pondéré est recalculé comme pour une entrée.
     */
    private void remettreEnRayon(Vente vente, String motif, Utilisateur utilisateur) {
        Map<Long, BigDecimal> quantitesBase = new LinkedHashMap<>();
        for (LigneVente ligne : vente.getLignesVente()) {
            quantitesBase.merge(ligne.getArticle().getId(), quantiteEnUniteDeStock(ligne), BigDecimal::add);
        }
        Map<Long, Article> articles = new HashMap<>();
        quantitesBase.keySet().stream().sorted().forEach(articleId -> articles.put(articleId,
            articleRepository.findByIdForUpdate(articleId)
                .orElseThrow(() -> new RessourceIntrouvableException("Article introuvable: " + articleId))));

        Map<Long, BigDecimal> stockCourant = new HashMap<>();
        articles.forEach((articleId, article) -> stockCourant.put(articleId, article.getQuantiteStock()));
        for (LigneVente ligne : vente.getLignesVente()) {
            Article article = articles.get(ligne.getArticle().getId());
            BigDecimal quantite = quantiteEnUniteDeStock(ligne);
            BigDecimal avant = stockCourant.get(article.getId());
            BigDecimal apres = avant.add(quantite);
            stockCourant.put(article.getId(), apres);
            BigDecimal cout = coutParUniteDeStock(ligne, article);
            article.setCmp(CoutMoyenPondere.apresEntree(avant, article.getCmp(), quantite, cout));
            article.setQuantiteStock(apres);

            MouvementStock mouvement = new MouvementStock();
            mouvement.setType(TypeMouvementStock.RETOUR_CLIENT);
            mouvement.setQuantite(quantite);
            mouvement.setStockAvant(avant);
            mouvement.setStockApres(apres);
            mouvement.setCoutUnitaire(cout);
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
            ligne.setMontantLigne(ligneProforma.getMontantLigne());
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

        List<String> avertissements = validerEtDecrementerStock(vente, username);
        vente.setStatut(StatutVente.VALIDEE);
        proforma.setStatut(StatutProforma.CONVERTIE);
        proformaRepository.save(proforma);
        Vente saved = venteRepository.save(vente);
        journalActiviteService.enregistrer("VALIDATION", "VENTE", saved.getId(),
            "Vente " + saved.getCode() + " issue du proforma " + proforma.getNumero(), username);
        return VentesMapper.toResponse(saved, avertissements);
    }

    /**
     * Sort les articles du stock et valide la vente. Un prix négocié sous le prix d'achat la fait refuser (voir
     * {@link #refuserPrixNegociesSousLeCout}). Renvoie les avertissements « vendu sous le prix d'achat » des lignes non
     * négociées (une promotion qui descend sous le coût) : elles passent, mais sont notées au journal des opérations pour que
     * le responsable les voie.
     */
    private List<String> validerEtDecrementerStock(Vente vente, String username) {
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

        Map<Long, BigDecimal> quantitesBase = new LinkedHashMap<>();
        for (LigneVente ligne : vente.getLignesVente()) {
            quantitesBase.merge(ligne.getArticle().getId(), quantiteEnUniteDeStock(ligne), BigDecimal::add);
        }

        Map<Long, Article> articles = new HashMap<>();
        quantitesBase.keySet().stream().sorted().forEach(articleId -> {
            Article article = articleRepository.findByIdForUpdate(articleId)
                .orElseThrow(() -> new RessourceIntrouvableException("Article introuvable: " + articleId));
            BigDecimal necessaire = quantitesBase.get(articleId);
            if (article.getQuantiteStock().compareTo(necessaire) < 0) {
                throw new ConflitRessourceException("Stock insuffisant pour l'article " + article.getReference()
                    + ": demandé " + Quantites.texte(necessaire) + ", disponible " + Quantites.texte(article.getQuantiteStock()));
            }
            articles.put(articleId, article);
        });

        // Avant de toucher à la vente ou au stock : un refus ne laisse aucune trace.
        refuserPrixNegociesSousLeCout(vente, articles);

        vente.setSessionCaisse(session);
        vente.setVendeur(vendeur);
        vente.setDateVente(LocalDateTime.now());
        vente.setStatut(StatutVente.VALIDEE);
        venteRepository.saveAndFlush(vente);

        if (vente.isVenteACredit()) {
            creerDetteVente(vente);
        }

        Map<Long, BigDecimal> stockCourant = new HashMap<>();
        articles.forEach((id, article) -> stockCourant.put(id, article.getQuantiteStock()));
        List<String> sousLeCout = new ArrayList<>();
        for (LigneVente ligne : vente.getLignesVente()) {
            Article article = articles.get(ligne.getArticle().getId());
            BigDecimal quantite = quantiteEnUniteDeStock(ligne);
            BigDecimal avant = stockCourant.get(article.getId());
            BigDecimal apres = avant.subtract(quantite);
            stockCourant.put(article.getId(), apres);
            article.setQuantiteStock(apres);
            // Une sortie ne change pas le CMP : elle sort à son niveau du moment, mémorisé sur la ligne (la marge de cette
            // vente ne bougera plus, quoi qu'il arrive aux prix d'achat ensuite).
            ligne.setCoutUnitaire(article.getCmp().multiply(facteurDe(ligne)).setScale(4, RoundingMode.HALF_UP));
            ligne.setCoutEstime(false);
            BigDecimal coutLigne = Quantites.montant(ligne.getCoutUnitaire(), ligne.getQuantite());
            if (ligne.getMontantLigne().compareTo(coutLigne) < 0) {
                sousLeCout.add(article.getReference() + " « " + article.getDesignation() + " » : " + Quantites.texte(ligne.getQuantite())
                    + " vendu(s) " + montantLisible(ligne.getMontantLigne()) + ", prix d'achat " + montantLisible(coutLigne));
            }
            creerMouvementStock(vente, ligne, article, quantite, avant, apres, vendeur, session);
        }
        articleRepository.saveAll(articles.values());
        journalActiviteService.enregistrer("VALIDATION", "VENTE", vente.getId(),
            "Validation de la vente " + vente.getCode() + ", total " + vente.getMontantTotal(), username);
        if (!sousLeCout.isEmpty()) {
            journalActiviteService.enregistrer("VENTE_SOUS_COUT", "VENTE", vente.getId(),
                "Vente " + vente.getCode() + " sous le prix d'achat : " + String.join(" ; ", sousLeCout), username);
        }
        return sousLeCout.stream().map(detail -> "Vendu sous le prix d'achat — " + detail).toList();
    }

    /**
     * Décision du propriétaire (novembre 2026) : une vente ne se valide pas si un prix négocié, par unité ou pour la ligne
     * entière, la place sous son prix d'achat. Le prix d'achat est le coût moyen de l'article (le même que celui de la marge et
     * de l'alerte après coup), multiplié par le facteur de l'unité vendue, au moment de la validation. Un prix égal au coût
     * passe ; un article au coût encore à 0 ne bloque jamais. Une ligne au prix catalogue, même réduite par une promotion,
     * n'est pas concernée. Tous les articles fautifs sont cités d'un coup pour que le vendeur corrige en une seule fois.
     */
    private void refuserPrixNegociesSousLeCout(Vente vente, Map<Long, Article> articles) {
        List<String> refus = new ArrayList<>();
        for (LigneVente ligne : vente.getLignesVente()) {
            if (!estPrixNegocie(ligne)) {
                continue;
            }
            Article article = articles.get(ligne.getArticle().getId());
            BigDecimal coutUnitaire = article.getCmp().multiply(facteurDe(ligne)).setScale(4, RoundingMode.HALF_UP);
            BigDecimal coutLigne = Quantites.montant(coutUnitaire, ligne.getQuantite());
            if (ligne.getMontantLigne().compareTo(coutLigne) < 0) {
                refus.add(article.getReference() + " « " + article.getDesignation() + " » : " + montantLisible(ligne.getMontantLigne())
                    + " proposé pour " + Quantites.texte(ligne.getQuantite()) + ", minimum " + montantLisible(coutLigne));
            }
        }
        if (!refus.isEmpty()) {
            throw new PrixSousLeCoutException("Prix négocié sous le prix d'achat : la vente n'est pas validée. "
                + "Relevez le prix négocié de " + String.join(" ; ", refus));
        }
    }

    /** Une ligne est négociée quand son prix unitaire est inférieur au prix catalogue relevé à la création de la ligne. */
    private static boolean estPrixNegocie(LigneVente ligne) {
        return ligne.getPrixCatalogue() != null && ligne.getPrixUnitaire() != null
            && ligne.getPrixUnitaire().compareTo(ligne.getPrixCatalogue()) < 0;
    }

    /** 150000.0000 -> « 150000 », 1234.5000 -> « 1234,5 » : un montant lisible dans un message au vendeur. */
    private static String montantLisible(BigDecimal montant) {
        return Quantites.texte(montant.setScale(2, RoundingMode.HALF_UP));
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

    private BigDecimal facteurDe(LigneVente ligne) {
        return ligne.getUniteVente() == null ? BigDecimal.ONE : ligne.getUniteVente().getFacteurConversion();
    }

    /** Coût d'UNE unité de stock à la vente, déduit du coût mémorisé sur la ligne (par unité vendue) ; CMP actuel à défaut. */
    private BigDecimal coutParUniteDeStock(LigneVente ligne, Article article) {
        if (ligne.getCoutUnitaire() == null) {
            return article.getCmp();
        }
        return ligne.getCoutUnitaire().divide(facteurDe(ligne), 4, RoundingMode.HALF_UP);
    }

    /** Quantité vendue convertie en unité de stock ; elle doit tenir en 3 décimales (sinon le stock serait arrondi en silence). */
    private BigDecimal quantiteEnUniteDeStock(LigneVente ligne) {
        try {
            return facteurDe(ligne).multiply(ligne.getQuantite()).setScale(Quantites.ECHELLE, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new ConflitRessourceException("Article " + ligne.getArticle().getReference() + " : la quantité convertie en "
                + "unité de stock aurait plus de 3 chiffres après la virgule ; arrondissez la quantité vendue");
        }
    }

    private void creerMouvementStock(
        Vente vente,
        LigneVente ligne,
        Article article,
        BigDecimal quantite,
        BigDecimal avant,
        BigDecimal apres,
        Utilisateur vendeur,
        SessionCaisse session
    ) {
        MouvementStock mouvement = new MouvementStock();
        mouvement.setType(TypeMouvementStock.SORTIE_VENTE);
        mouvement.setQuantite(quantite);
        mouvement.setStockAvant(avant);
        mouvement.setStockApres(apres);
        mouvement.setCoutUnitaire(article.getCmp());
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

    /**
     * Prix d'une ligne de document (vente ou proforma). Deux façons de négocier :
     * <ul>
     *   <li>un prix par unité ({@code prixUnitaire}), sur lequel la promotion éventuelle s'applique encore ;</li>
     *   <li>un prix pour la ligne entière ({@code montantNegocie}) : il est définitif, sans promotion par-dessus, et le prix
     *       unitaire affiché en est déduit.</li>
     * </ul>
     */
    private record PrixLigne(
        BigDecimal prixCatalogue, BigDecimal prixUnitaire, Promotion promotion, BigDecimal remiseUnitaire, BigDecimal montantLigne
    ) {}

    private PrixLigne prixLigne(Article article, UniteVente unite, LigneDocumentRequest demande, LocalDateTime moment) {
        BigDecimal prixCatalogue = prixCatalogue(article, unite);
        BigDecimal quantite = demande.quantite();
        if (demande.montantNegocie() != null) {
            BigDecimal totalCatalogue = Quantites.montant(prixCatalogue, quantite);
            if (demande.montantNegocie().compareTo(totalCatalogue) > 0) {
                throw new RequeteInvalideException("Le prix négocié de la ligne " + article.getReference() + " ("
                    + demande.montantNegocie().toPlainString() + ") ne peut pas dépasser son total au prix catalogue ("
                    + totalCatalogue.setScale(2, RoundingMode.HALF_UP).toPlainString() + ")");
            }
            BigDecimal montant = demande.montantNegocie().setScale(4, RoundingMode.HALF_UP);
            return new PrixLigne(prixCatalogue, montant.divide(quantite, 4, RoundingMode.HALF_UP), null, BigDecimal.ZERO, montant);
        }
        BigDecimal prix = prixApplique(demande.prixUnitaire(), prixCatalogue, article);
        Promotion promotion = promotionService.promotionChoisie(article, demande.promotionId(), moment);
        BigDecimal remiseUnitaire = promotionService.montantRemise(promotion, prix);
        return new PrixLigne(prixCatalogue, prix, promotion, remiseUnitaire, Quantites.montant(prix.subtract(remiseUnitaire), quantite));
    }

    private LigneVente creerLigneVente(Vente vente, LigneDocumentRequest request) {
        Article article = trouverArticle(request.articleId());
        UniteVente unite = request.uniteVenteId() == null ? null : trouverUniteVente(request.uniteVenteId(), article);
        PrixLigne prix = prixLigne(article, unite, request, LocalDateTime.now());
        LigneVente ligne = new LigneVente();
        ligne.setPrixCatalogue(prix.prixCatalogue());
        ligne.setVente(vente);
        ligne.setArticle(article);
        ligne.setUniteVente(unite);
        ligne.setQuantite(request.quantite());
        ligne.setPrixUnitaire(prix.prixUnitaire());
        ligne.setPromotion(prix.promotion());
        ligne.setRemiseUnitaire(prix.remiseUnitaire());
        ligne.setMontantLigne(prix.montantLigne());
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
            PrixLigne prix = prixLigne(article, unite, demande, proforma.getDateEmission().atStartOfDay());
            LigneProforma ligne = new LigneProforma();
            ligne.setProforma(proforma);
            ligne.setArticle(article);
            ligne.setUniteVente(unite);
            ligne.setQuantite(demande.quantite());
            ligne.setPrixUnitaire(prix.prixUnitaire());
            ligne.setPrixCatalogue(prix.prixCatalogue());
            ligne.setMontantLigne(prix.montantLigne());
            ligne.setPromotion(prix.promotion());
            ligne.setRemiseUnitaire(prix.remiseUnitaire());
            proforma.getLignes().add(ligne);
            total = total.add(prix.montantLigne());
        }
        proforma.setMontantTotal(total);
    }

    private BigDecimal calculerTotalVente(List<LigneVente> lignes) {
        return lignes.stream().map(LigneVente::getMontantLigne).reduce(BigDecimal.ZERO, BigDecimal::add);
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
