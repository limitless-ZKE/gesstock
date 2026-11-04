package com.gesstock.gesstock.caisse.service;

import com.gesstock.gesstock.caisse.dto.MouvementCaisseRequest;
import com.gesstock.gesstock.caisse.dto.MouvementCaisseResponse;
import com.gesstock.gesstock.entities.MouvementCaisse;
import com.gesstock.gesstock.entities.ModeReglement;
import com.gesstock.gesstock.entities.OrigineMouvementCaisse;
import com.gesstock.gesstock.entities.SessionCaisse;
import com.gesstock.gesstock.entities.StatutSessionCaisse;
import com.gesstock.gesstock.entities.StatutVente;
import com.gesstock.gesstock.entities.TypeMouvementCaisse;
import com.gesstock.gesstock.entities.Utilisateur;
import com.gesstock.gesstock.repositories.MouvementCaisseRepository;
import com.gesstock.gesstock.repositories.SessionCaisseRepository;
import com.gesstock.gesstock.repositories.ReglementClientRepository;
import com.gesstock.gesstock.repositories.UtilisateurRepository;
import com.gesstock.gesstock.repositories.VenteRepository;
import com.gesstock.gesstock.common.exception.ConflitRessourceException;
import com.gesstock.gesstock.common.exception.RessourceIntrouvableException;
import com.gesstock.gesstock.journal.service.JournalActiviteService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.math.BigDecimal;

@Service
@Transactional
public class MouvementCaisseService {

    private final MouvementCaisseRepository mouvementRepository;
    private final SessionCaisseRepository sessionRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final JournalActiviteService journalService;
    private final VenteRepository venteRepository;
    private final ReglementClientRepository reglementRepository;

    public MouvementCaisseService(
        MouvementCaisseRepository mouvementRepository,
        SessionCaisseRepository sessionRepository,
        UtilisateurRepository utilisateurRepository,
        JournalActiviteService journalService,
        VenteRepository venteRepository,
        ReglementClientRepository reglementRepository
    ) {
        this.mouvementRepository = mouvementRepository;
        this.sessionRepository = sessionRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.journalService = journalService;
        this.venteRepository = venteRepository;
        this.reglementRepository = reglementRepository;
    }

    @Transactional(readOnly = true)
    public List<MouvementCaisseResponse> lister(Long sessionId) {
        SessionCaisse session = trouverSession(sessionId);
        return mouvementRepository.findBySessionCaisseIdOrderByDateMouvementDesc(session.getId())
            .stream().map(MouvementCaisseService::toResponse).toList();
    }

    /** Une sortie saisie à la main : c'est une dépense dans les statistiques. */
    public MouvementCaisseResponse enregistrer(MouvementCaisseRequest request, String username) {
        return enregistrer(request, username, OrigineMouvementCaisse.MANUEL);
    }

    /** Une sortie d'espèces ; l'origine dit si elle compte comme dépense (MANUEL) ou est déjà comptée ailleurs. */
    public MouvementCaisseResponse enregistrer(MouvementCaisseRequest request, String username, OrigineMouvementCaisse origine) {
        SessionCaisse session = sessionRepository.findByJourneeAndStatutForUpdate(
                LocalDate.now(), StatutSessionCaisse.OUVERTE)
            .orElseThrow(() -> new ConflitRessourceException(
                "Une session de caisse ouverte est requise pour enregistrer une sortie"));
        Utilisateur utilisateur = utilisateurRepository.findByUsername(username)
            .orElseThrow(() -> new RessourceIntrouvableException("Utilisateur connecté introuvable"));
        if (!utilisateur.isActif()) {
            throw new ConflitRessourceException("Un utilisateur désactivé ne peut pas enregistrer une sortie de caisse");
        }
        BigDecimal disponible = soldeEspecesDisponible(session);
        if (request.montant().compareTo(disponible) > 0) {
            throw new ConflitRessourceException(
                "La sortie dépasse le solde théorique disponible de " + disponible.toPlainString());
        }

        MouvementCaisse mouvement = new MouvementCaisse();
        mouvement.setSessionCaisse(session);
        mouvement.setEffectuePar(utilisateur);
        mouvement.setType(TypeMouvementCaisse.SORTIE);
        mouvement.setOrigine(origine);
        mouvement.setMotif(request.motif().trim());
        mouvement.setBeneficiaire(request.beneficiaire().trim());
        mouvement.setMontant(request.montant());
        MouvementCaisse saved = mouvementRepository.save(mouvement);
        journalService.enregistrer("SORTIE_CAISSE", "SESSION_CAISSE", session.getId(),
            request.motif().trim() + " · " + request.beneficiaire().trim() + " · " + request.montant(), username);
        return toResponse(saved);
    }

    /**
     * Un apport de fonds : de l'argent ajouté dans la caisse du jour (apport du patron, monnaie reçue de la banque…). Il
     * augmente les espèces de la caisse ; ce n'est jamais du chiffre d'affaires. Réservé aux responsables (voir SecurityConfig).
     */
    public MouvementCaisseResponse enregistrerEntree(MouvementCaisseRequest request, String username) {
        SessionCaisse session = sessionRepository.findByJourneeAndStatutForUpdate(
                LocalDate.now(), StatutSessionCaisse.OUVERTE)
            .orElseThrow(() -> new ConflitRessourceException(
                "Une session de caisse ouverte est requise pour enregistrer une entrée de fonds"));
        Utilisateur utilisateur = utilisateurRepository.findByUsername(username)
            .orElseThrow(() -> new RessourceIntrouvableException("Utilisateur connecté introuvable"));
        if (!utilisateur.isActif()) {
            throw new ConflitRessourceException("Un utilisateur désactivé ne peut pas enregistrer une entrée de fonds");
        }
        MouvementCaisse mouvement = new MouvementCaisse();
        mouvement.setSessionCaisse(session);
        mouvement.setEffectuePar(utilisateur);
        mouvement.setType(TypeMouvementCaisse.ENTREE);
        mouvement.setOrigine(OrigineMouvementCaisse.MANUEL);
        mouvement.setMotif(request.motif().trim());
        mouvement.setBeneficiaire(request.beneficiaire().trim());
        mouvement.setMontant(request.montant());
        MouvementCaisse saved = mouvementRepository.save(mouvement);
        journalService.enregistrer("ENTREE_FONDS", "SESSION_CAISSE", session.getId(),
            request.motif().trim() + " · " + request.beneficiaire().trim() + " · " + request.montant(), username);
        return toResponse(saved);
    }

    /**
     * Espèces que la caisse est censée contenir : fond de caisse, encaissements des ventes validées en espèces, règlements
     * clients en espèces, plus les entrées de fonds, moins les sorties. Une vente annulée n'y figure plus : son remboursement n'est donc PAS une sortie à
     * enregistrer en plus (ce serait la déduire deux fois).
     */
    @Transactional(readOnly = true)
    public BigDecimal soldeEspecesDisponible(SessionCaisse session) {
        return session.getFondDeCaisse()
            .add(valeurOuZero(venteRepository.encaissementsEspecesSession(
                session.getId(), StatutVente.VALIDEE, ModeReglement.ESPECES)))
            .add(valeurOuZero(reglementRepository.totalParSessionEtMode(session.getId(), ModeReglement.ESPECES)))
            .add(valeurOuZero(mouvementRepository.totalEntrees(session.getId())))
            .subtract(valeurOuZero(mouvementRepository.totalSorties(session.getId())));
    }

    private SessionCaisse trouverSession(Long id) {
        return sessionRepository.findById(id)
            .orElseThrow(() -> new RessourceIntrouvableException("Session de caisse introuvable: " + id));
    }

    private static MouvementCaisseResponse toResponse(MouvementCaisse mouvement) {
        return new MouvementCaisseResponse(
            mouvement.getId(),
            mouvement.getSessionCaisse().getId(),
            mouvement.getType().name(),
            mouvement.getOrigine().name(),
            mouvement.getMotif(),
            mouvement.getBeneficiaire(),
            mouvement.getMontant(),
            mouvement.getDateMouvement(),
            mouvement.getEffectuePar().getUsername()
        );
    }

    private BigDecimal valeurOuZero(Number value) {
        return value == null ? BigDecimal.ZERO : new BigDecimal(value.toString());
    }

    private BigDecimal valeurOuZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
