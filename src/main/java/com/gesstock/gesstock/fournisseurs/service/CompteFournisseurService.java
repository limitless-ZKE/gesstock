package com.gesstock.gesstock.fournisseurs.service;

import com.gesstock.gesstock.caisse.dto.MouvementCaisseRequest;
import com.gesstock.gesstock.caisse.dto.MouvementCaisseResponse;
import com.gesstock.gesstock.caisse.service.MouvementCaisseService;
import com.gesstock.gesstock.common.exception.ConflitRessourceException;
import com.gesstock.gesstock.common.exception.RessourceIntrouvableException;
import com.gesstock.gesstock.entities.Approvisionnement;
import com.gesstock.gesstock.entities.AvoirFournisseur;
import com.gesstock.gesstock.entities.DetteFournisseur;
import com.gesstock.gesstock.entities.Fournisseur;
import com.gesstock.gesstock.entities.LigneApprovisionnement;
import com.gesstock.gesstock.entities.ModeReglement;
import com.gesstock.gesstock.entities.ReglementFournisseur;
import com.gesstock.gesstock.entities.SourceReglementFournisseur;
import com.gesstock.gesstock.entities.StatutDette;
import com.gesstock.gesstock.entities.Utilisateur;
import com.gesstock.gesstock.fournisseurs.dto.AvoirFournisseurRequest;
import com.gesstock.gesstock.fournisseurs.dto.AvoirFournisseurResponse;
import com.gesstock.gesstock.fournisseurs.dto.CompteFournisseurResponse;
import com.gesstock.gesstock.fournisseurs.dto.CompteFournisseurResume;
import com.gesstock.gesstock.fournisseurs.dto.DetteFournisseurResponse;
import com.gesstock.gesstock.fournisseurs.dto.ReglementFournisseurRequest;
import com.gesstock.gesstock.fournisseurs.dto.ReglementFournisseurResponse;
import com.gesstock.gesstock.journal.service.JournalActiviteService;
import com.gesstock.gesstock.repositories.AvoirFournisseurRepository;
import com.gesstock.gesstock.repositories.DetteFournisseurRepository;
import com.gesstock.gesstock.repositories.FournisseurRepository;
import com.gesstock.gesstock.repositories.MouvementCaisseRepository;
import com.gesstock.gesstock.repositories.ReglementFournisseurRepository;
import com.gesstock.gesstock.repositories.UtilisateurRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gesstock.gesstock.stock.Quantites;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Compte d'un fournisseur : ce que nous lui devons (une dette par bon reçu), ce que nous lui avons payé, et les avoirs qu'il
 * nous accorde.
 *
 * <p>Toute écriture commence par verrouiller la ligne du fournisseur : paiements, nouveaux bons et avoirs d'un même
 * fournisseur sont ainsi sérialisés, et l'imputation des avoirs ne peut ni consommer deux fois le même crédit ni régler deux
 * fois la même dette. Ordre des verrous dans l'application : articles, puis fournisseur, puis session de caisse.
 */
@Service
@Transactional
public class CompteFournisseurService {

    private static final int ECHELLE_MONETAIRE = 4;

    private final FournisseurRepository fournisseurRepository;
    private final DetteFournisseurRepository detteRepository;
    private final AvoirFournisseurRepository avoirRepository;
    private final ReglementFournisseurRepository reglementRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final MouvementCaisseService mouvementCaisseService;
    private final MouvementCaisseRepository mouvementCaisseRepository;
    private final JournalActiviteService journalActiviteService;

    public CompteFournisseurService(
        FournisseurRepository fournisseurRepository,
        DetteFournisseurRepository detteRepository,
        AvoirFournisseurRepository avoirRepository,
        ReglementFournisseurRepository reglementRepository,
        UtilisateurRepository utilisateurRepository,
        MouvementCaisseService mouvementCaisseService,
        MouvementCaisseRepository mouvementCaisseRepository,
        JournalActiviteService journalActiviteService
    ) {
        this.fournisseurRepository = fournisseurRepository;
        this.detteRepository = detteRepository;
        this.avoirRepository = avoirRepository;
        this.reglementRepository = reglementRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.mouvementCaisseService = mouvementCaisseService;
        this.mouvementCaisseRepository = mouvementCaisseRepository;
        this.journalActiviteService = journalActiviteService;
    }

    // --- Lecture ---

    @Transactional(readOnly = true)
    public List<CompteFournisseurResume> lister() {
        Map<Long, BigDecimal> soldes = parFournisseur(detteRepository.soldesParFournisseur());
        Map<Long, BigDecimal> credits = parFournisseur(avoirRepository.creditsParFournisseur());
        return fournisseurRepository.findAllByOrderByNomAsc().stream()
            .map(fournisseur -> new CompteFournisseurResume(
                fournisseur.getId(),
                fournisseur.getNom(),
                soldes.getOrDefault(fournisseur.getId(), BigDecimal.ZERO),
                credits.getOrDefault(fournisseur.getId(), BigDecimal.ZERO)))
            .toList();
    }

    @Transactional(readOnly = true)
    public CompteFournisseurResponse obtenir(Long fournisseurId) {
        Fournisseur fournisseur = trouverFournisseur(fournisseurId);
        List<DetteFournisseur> dettes = detteRepository.findDuFournisseur(fournisseurId);
        List<AvoirFournisseur> avoirs = avoirRepository.findByFournisseurIdOrderByDateCreationDescIdDesc(fournisseurId);
        // Un seul chargement des règlements du fournisseur, regroupés par dette : pas une requête par dette.
        Map<Long, List<ReglementFournisseurResponse>> reglements = reglementRepository
            .findByFournisseurIdOrderByDateReglementAscIdAsc(fournisseurId).stream()
            .map(CompteFournisseurService::versReponse)
            .collect(Collectors.groupingBy(ReglementFournisseurResponse::detteId, LinkedHashMap::new, Collectors.toList()));

        BigDecimal soldeDu = dettes.stream().filter(CompteFournisseurService::ouverte)
            .map(DetteFournisseur::solde).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal credit = avoirs.stream().map(AvoirFournisseur::restant).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new CompteFournisseurResponse(
            fournisseur.getId(),
            fournisseur.getNom(),
            soldeDu,
            credit,
            dettes.stream().map(dette -> versReponse(dette, reglements.getOrDefault(dette.getId(), List.of()))).toList(),
            avoirs.stream().map(CompteFournisseurService::versReponse).toList());
    }

    // --- Écriture ---

    /**
     * Appelée à la réception d'un bon : crée la dette du bon, puis y impute les avoirs encore disponibles. Un bon de valeur
     * nulle (échantillons gratuits) ne crée aucune dette.
     */
    public void enregistrerDette(Approvisionnement bon, String username) {
        BigDecimal total = totalDuBon(bon);
        if (total.signum() <= 0) {
            return;
        }
        Fournisseur fournisseur = verrouiller(bon.getFournisseur().getId());
        DetteFournisseur dette = new DetteFournisseur();
        dette.setFournisseur(fournisseur);
        dette.setApprovisionnement(bon);
        dette.setDateCreation(LocalDate.now());
        dette.setMontantInitial(total);
        detteRepository.saveAndFlush(dette);
        journalActiviteService.enregistrer("CREATION", "DETTE_FOURNISSEUR", dette.getId(),
            "Dette de " + total.toPlainString() + " pour le bon " + bon.getNumero(), username);
        imputerAvoirs(fournisseur, utilisateur(username));
    }

    /**
     * Appelée quand un bon déjà reçu est corrigé : sa dette suit le nouveau total (et le nouveau fournisseur). Elle ne descend
     * jamais sous ce qui a déjà été réglé (paiements et avoirs) ; un bon ramené à zéro sans rien de réglé n'a plus de dette.
     */
    public void corrigerDette(Approvisionnement bon, Long ancienFournisseurId, String username) {
        BigDecimal total = totalDuBon(bon);
        Long nouveauFournisseurId = bon.getFournisseur().getId();
        // verrous des comptes dans l'ordre des identifiants : deux opérations concurrentes ne peuvent pas s'interbloquer
        Map<Long, Fournisseur> comptes = new LinkedHashMap<>();
        java.util.stream.Stream.of(ancienFournisseurId, nouveauFournisseurId).distinct().sorted()
            .forEach(id -> comptes.put(id, verrouiller(id)));
        Optional<DetteFournisseur> existante = detteRepository.findByApprovisionnementIdForUpdate(bon.getId());
        if (existante.isEmpty()) {
            if (total.signum() > 0) {
                enregistrerDette(bon, username);
            }
            return;
        }
        DetteFournisseur dette = existante.get();
        BigDecimal ancienMontant = dette.getMontantInitial();
        if (!dette.getFournisseur().getId().equals(nouveauFournisseurId) && dette.getMontantRegle().signum() > 0) {
            throw new ConflitRessourceException("Le bon " + bon.getNumero() + " a déjà été réglé en partie à "
                + dette.getFournisseur().getNom() + " : il ne peut plus changer de fournisseur");
        }
        if (total.compareTo(dette.getMontantRegle()) < 0) {
            throw new ConflitRessourceException("Le fournisseur a déjà reçu " + dette.getMontantRegle().toPlainString()
                + " pour le bon " + bon.getNumero() + " (paiements et avoirs) : le nouveau total du bon ("
                + total.toPlainString() + ") ne peut pas être inférieur");
        }
        if (total.signum() == 0) {
            detteRepository.delete(dette);
            journalActiviteService.enregistrer("MODIFICATION", "DETTE_FOURNISSEUR", dette.getId(),
                "Dette du bon " + bon.getNumero() + " supprimée : le bon corrigé ne vaut plus rien", username);
            return;
        }
        Fournisseur fournisseur = comptes.get(nouveauFournisseurId);
        dette.setFournisseur(fournisseur);
        dette.corrigerMontant(total);
        detteRepository.saveAndFlush(dette);
        journalActiviteService.enregistrer("MODIFICATION", "DETTE_FOURNISSEUR", dette.getId(),
            "Dette du bon " + bon.getNumero() + " corrigée : " + ancienMontant.toPlainString() + " → " + total.toPlainString()
                + (ancienFournisseurId.equals(nouveauFournisseurId) ? "" : ", désormais due à " + fournisseur.getNom()), username);
        imputerAvoirs(fournisseur, utilisateur(username));
    }

    /** Total d'un bon : somme des lignes (prix d'achat × quantité), à l'échelle monétaire. */
    private static BigDecimal totalDuBon(Approvisionnement bon) {
        BigDecimal total = BigDecimal.ZERO;
        for (LigneApprovisionnement ligne : bon.getLignes()) {
            total = total.add(Quantites.montant(ligne.getPrixUnitaire(), ligne.getQuantite()));
        }
        return total.setScale(ECHELLE_MONETAIRE, RoundingMode.UNNECESSARY);
    }

    public ReglementFournisseurResponse payer(Long detteId, ReglementFournisseurRequest request, String username) {
        Long fournisseurId = detteRepository.findFournisseurId(detteId)
            .orElseThrow(() -> new RessourceIntrouvableException("Dette fournisseur introuvable: " + detteId));
        Fournisseur fournisseur = verrouiller(fournisseurId);
        DetteFournisseur dette = detteRepository.findByIdForUpdate(detteId)
            .orElseThrow(() -> new RessourceIntrouvableException("Dette fournisseur introuvable: " + detteId));
        if (!ouverte(dette)) {
            throw new ConflitRessourceException("Cette dette est déjà réglée");
        }
        BigDecimal montant = request.montant().setScale(ECHELLE_MONETAIRE, RoundingMode.UNNECESSARY);
        BigDecimal solde = dette.solde();
        if (montant.compareTo(solde) > 0) {
            throw new ConflitRessourceException("Le paiement dépasse le solde restant de " + solde.toPlainString());
        }
        Utilisateur utilisateur = utilisateurActif(username);

        ReglementFournisseur reglement = new ReglementFournisseur();
        reglement.setDette(dette);
        reglement.setFournisseur(fournisseur);
        reglement.setMontant(montant);
        reglement.setSource(SourceReglementFournisseur.PAIEMENT);
        reglement.setModeReglement(request.modeReglement());
        reglement.setReference(request.reference() == null || request.reference().isBlank() ? null : request.reference().trim());
        reglement.setDateReglement(LocalDateTime.now());
        reglement.setEffectuePar(utilisateur);
        if (request.modeReglement() == ModeReglement.ESPECES) {
            // L'argent quitte la caisse : sortie tracée (session ouverte et solde suffisant exigés par la caisse elle-même).
            MouvementCaisseResponse sortie = mouvementCaisseService.enregistrer(new MouvementCaisseRequest(
                "Règlement fournisseur : bon " + dette.getApprovisionnement().getNumero(), fournisseur.getNom(), montant), username);
            reglement.setMouvementCaisse(mouvementCaisseRepository.getReferenceById(sortie.id()));
        }
        dette.imputer(montant);
        detteRepository.save(dette);
        reglementRepository.save(reglement);
        journalActiviteService.enregistrer("REGLEMENT", "DETTE_FOURNISSEUR", dette.getId(),
            "Paiement de " + montant.toPlainString() + " " + request.modeReglement()
                + " pour le bon " + dette.getApprovisionnement().getNumero(), username);
        return versReponse(reglement);
    }

    /** Avoir saisi à la main (ristourne, geste commercial) : il s'impute aussitôt sur les dettes les plus anciennes. */
    public AvoirFournisseurResponse creerAvoir(Long fournisseurId, AvoirFournisseurRequest request, String username) {
        return versReponse(emettreAvoir(fournisseurId, request.montant(), request.motif().trim(), username));
    }

    /**
     * Enregistre un avoir du fournisseur et l'impute aussitôt sur ses dettes les plus anciennes ; le reliquat reste
     * disponible pour les prochains bons. Point d'entrée commun à l'avoir manuel et à l'avoir d'un retour de marchandise.
     */
    public AvoirFournisseur emettreAvoir(Long fournisseurId, BigDecimal montant, String motif, String username) {
        Fournisseur fournisseur = verrouiller(fournisseurId);
        Utilisateur utilisateur = utilisateurActif(username);
        AvoirFournisseur avoir = new AvoirFournisseur();
        avoir.setFournisseur(fournisseur);
        avoir.setMontant(montant.setScale(ECHELLE_MONETAIRE, RoundingMode.UNNECESSARY));
        avoir.setMotif(motif);
        avoir.setCreePar(utilisateur);
        avoir.setDateCreation(LocalDateTime.now());
        avoirRepository.saveAndFlush(avoir);
        journalActiviteService.enregistrer("CREATION", "AVOIR_FOURNISSEUR", avoir.getId(),
            "Avoir de " + avoir.getMontant().toPlainString() + " de " + fournisseur.getNom() + " : " + motif, username);
        imputerAvoirs(fournisseur, utilisateur);
        return avoir;
    }

    /**
     * Impute les avoirs disponibles (du plus ancien au plus récent) sur les dettes ouvertes (de la plus ancienne à la plus
     * récente). Le compte du fournisseur doit déjà être verrouillé par l'appelant.
     */
    private void imputerAvoirs(Fournisseur fournisseur, Utilisateur utilisateur) {
        List<AvoirFournisseur> avoirs = avoirRepository.findDisponibles(fournisseur.getId());
        if (avoirs.isEmpty()) {
            return;
        }
        List<DetteFournisseur> dettes = detteRepository.findOuvertes(fournisseur.getId());
        int prochaine = 0;
        for (AvoirFournisseur avoir : avoirs) {
            while (avoir.restant().signum() > 0 && prochaine < dettes.size()) {
                DetteFournisseur dette = dettes.get(prochaine);
                BigDecimal montant = avoir.restant().min(dette.solde());

                ReglementFournisseur reglement = new ReglementFournisseur();
                reglement.setDette(dette);
                reglement.setFournisseur(fournisseur);
                reglement.setMontant(montant);
                reglement.setSource(SourceReglementFournisseur.AVOIR);
                reglement.setAvoir(avoir);
                reglement.setDateReglement(LocalDateTime.now());
                reglement.setEffectuePar(utilisateur);
                reglementRepository.save(reglement);

                avoir.setMontantUtilise(avoir.getMontantUtilise().add(montant));
                dette.imputer(montant);
                if (!ouverte(dette)) {
                    prochaine++;
                }
            }
            if (prochaine >= dettes.size()) {
                break;
            }
        }
        avoirRepository.saveAll(avoirs);
        detteRepository.saveAll(dettes);
    }

    // --- Utilitaires ---

    private Fournisseur verrouiller(Long fournisseurId) {
        return fournisseurRepository.findByIdForUpdate(fournisseurId)
            .orElseThrow(() -> new RessourceIntrouvableException("Fournisseur introuvable: " + fournisseurId));
    }

    private Fournisseur trouverFournisseur(Long fournisseurId) {
        return fournisseurRepository.findById(fournisseurId)
            .orElseThrow(() -> new RessourceIntrouvableException("Fournisseur introuvable: " + fournisseurId));
    }

    private Utilisateur utilisateur(String username) {
        return utilisateurRepository.findByUsername(username).orElse(null);
    }

    private Utilisateur utilisateurActif(String username) {
        Utilisateur utilisateur = utilisateurRepository.findByUsername(username)
            .orElseThrow(() -> new RessourceIntrouvableException("Utilisateur connecté introuvable"));
        if (!utilisateur.isActif()) {
            throw new ConflitRessourceException("Un utilisateur désactivé ne peut pas enregistrer cette opération");
        }
        return utilisateur;
    }

    private static boolean ouverte(DetteFournisseur dette) {
        return dette.getStatut() == StatutDette.EN_COURS || dette.getStatut() == StatutDette.PARTIELLEMENT_REGLEE;
    }

    private static Map<Long, BigDecimal> parFournisseur(List<Object[]> lignes) {
        Map<Long, BigDecimal> montants = new LinkedHashMap<>();
        for (Object[] ligne : lignes) {
            montants.put(((Number) ligne[0]).longValue(), (BigDecimal) ligne[1]);
        }
        return montants;
    }

    private static DetteFournisseurResponse versReponse(DetteFournisseur dette, List<ReglementFournisseurResponse> reglements) {
        return new DetteFournisseurResponse(
            dette.getId(),
            dette.getDateCreation(),
            dette.getApprovisionnement().getId(),
            dette.getApprovisionnement().getNumero(),
            dette.getMontantInitial(),
            dette.getMontantRegle(),
            dette.solde(),
            dette.getStatut(),
            reglements);
    }

    private static ReglementFournisseurResponse versReponse(ReglementFournisseur reglement) {
        return new ReglementFournisseurResponse(
            reglement.getId(),
            reglement.getDette().getId(),
            reglement.getMontant(),
            reglement.getSource(),
            reglement.getModeReglement(),
            reglement.getReference(),
            reglement.getDateReglement(),
            reglement.getEffectuePar() == null ? null : reglement.getEffectuePar().getUsername(),
            reglement.getAvoir() == null ? null : reglement.getAvoir().getId());
    }

    private static AvoirFournisseurResponse versReponse(AvoirFournisseur avoir) {
        return new AvoirFournisseurResponse(
            avoir.getId(),
            avoir.getDateCreation(),
            avoir.getMontant(),
            avoir.getMontantUtilise(),
            avoir.restant(),
            avoir.getMotif(),
            avoir.getCreePar().getUsername());
    }
}
