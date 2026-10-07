package com.gesstock.gesstock.referentiels.service;

import com.gesstock.gesstock.entities.SessionCaisse;
import com.gesstock.gesstock.entities.ModeReglement;
import com.gesstock.gesstock.entities.StatutSessionCaisse;
import com.gesstock.gesstock.entities.Utilisateur;
import com.gesstock.gesstock.repositories.SessionCaisseRepository;
import com.gesstock.gesstock.repositories.MouvementCaisseRepository;
import com.gesstock.gesstock.repositories.ReglementClientRepository;
import com.gesstock.gesstock.repositories.VenteRepository;
import com.gesstock.gesstock.repositories.UtilisateurRepository;
import com.gesstock.gesstock.referentiels.dto.ClotureSessionRequest;
import com.gesstock.gesstock.referentiels.dto.OuvertureSessionRequest;
import com.gesstock.gesstock.referentiels.dto.SessionCaisseResponse;
import com.gesstock.gesstock.common.exception.ConflitRessourceException;
import com.gesstock.gesstock.common.exception.RequeteInvalideException;
import com.gesstock.gesstock.common.exception.RessourceIntrouvableException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.gesstock.gesstock.journal.service.JournalActiviteService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.math.BigDecimal;

@Service
@Transactional
public class SessionCaisseService {

    private final SessionCaisseRepository sessionCaisseRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final JournalActiviteService journalActiviteService;
    private final VenteRepository venteRepository;
    private final ReglementClientRepository reglementRepository;
    private final MouvementCaisseRepository mouvementRepository;

    public SessionCaisseService(
        SessionCaisseRepository sessionCaisseRepository,
        UtilisateurRepository utilisateurRepository,
        JournalActiviteService journalActiviteService,
        VenteRepository venteRepository,
        ReglementClientRepository reglementRepository,
        MouvementCaisseRepository mouvementRepository
    ) {
        this.sessionCaisseRepository = sessionCaisseRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.journalActiviteService = journalActiviteService;
        this.venteRepository = venteRepository;
        this.reglementRepository = reglementRepository;
        this.mouvementRepository = mouvementRepository;
    }

    @Transactional(readOnly = true)
    public List<SessionCaisseResponse> lister(LocalDate journee) {
        return sessionCaisseRepository.findByJourneeOrderByOuverteLeDesc(journee).stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public SessionCaisseResponse obtenir(Long id) {
        return toResponse(trouver(id));
    }

    /**
     * Session actuellement ouverte : celle d'aujourd'hui, ou à défaut une journée antérieure restée ouverte
     * (signalée par {@code journeeDepassee}) qu'il faut clôturer avant d'ouvrir celle du jour.
     */
    @Transactional(readOnly = true)
    public SessionCaisseResponse sessionOuverteCourante() {
        LocalDate aujourdHui = LocalDate.now();
        SessionCaisse session = sessionCaisseRepository.findByJourneeAndStatut(aujourdHui, StatutSessionCaisse.OUVERTE)
            .or(() -> sessionCaisseRepository.findFirstByStatutOrderByOuverteLeDesc(StatutSessionCaisse.OUVERTE))
            .orElseThrow(() -> new RessourceIntrouvableException("Aucune session ouverte pour le " + aujourdHui));
        return toResponse(session);
    }

    @Transactional(readOnly = true)
    public SessionCaisseResponse sessionOuverte(LocalDate journee) {
        SessionCaisse session = sessionCaisseRepository.findByJourneeOrderByOuverteLeDesc(journee).stream()
            .filter(candidate -> candidate.getStatut() == StatutSessionCaisse.OUVERTE)
            .findFirst()
            .orElseThrow(() -> new RessourceIntrouvableException("Aucune session ouverte pour le " + journee));
        return toResponse(session);
    }

    public SessionCaisseResponse ouvrir(OuvertureSessionRequest request, String username) {
        // La date de la journée vient toujours du calendrier du serveur : elle ne se choisit pas.
        LocalDate journee = LocalDate.now();
        if (request.journee() != null && !request.journee().equals(journee)) {
            throw new RequeteInvalideException(
                "La date de la journée est fixée par le serveur (" + journee + ") et ne peut pas être choisie");
        }
        if (sessionCaisseRepository.existsByJournee(journee)) {
            throw new ConflitRessourceException("Une journée de caisse existe déjà pour le " + journee);
        }
        sessionCaisseRepository.findFirstByStatutOrderByOuverteLeDesc(StatutSessionCaisse.OUVERTE).ifPresent(restee -> {
            throw new ConflitRessourceException("La journée du " + restee.getJournee()
                + " n'a pas été clôturée : clôturez-la avant d'ouvrir celle d'aujourd'hui");
        });

        Utilisateur utilisateur = trouverUtilisateurActif(username);
        SessionCaisse session = new SessionCaisse();
        session.setJournee(journee);
        session.setCode("CAISSE-" + journee.format(DateTimeFormatter.BASIC_ISO_DATE) + "-" +
            DateTimeFormatter.ofPattern("HHmmssSSS").format(LocalDateTime.now()));
        session.setOuverteLe(LocalDateTime.now());
        session.setFondDeCaisse(request.fondDeCaisse());
        session.setStatut(StatutSessionCaisse.OUVERTE);
        session.setOuvertePar(utilisateur);
        SessionCaisse saved = sessionCaisseRepository.save(session);
        journalActiviteService.enregistrer("OUVERTURE", "SESSION_CAISSE", saved.getId(),
            "Ouverture de journée avec fond de caisse " + saved.getFondDeCaisse(), username);
        return toResponse(saved);
    }

    public SessionCaisseResponse cloturer(Long id, ClotureSessionRequest request, String username) {
        SessionCaisse session = sessionCaisseRepository.findByIdForUpdate(id)
            .orElseThrow(() -> new RessourceIntrouvableException("Session de caisse introuvable: " + id));
        if (session.getStatut() != StatutSessionCaisse.OUVERTE) {
            throw new ConflitRessourceException("La session de caisse est déjà clôturée");
        }

        session.setMontantCloture(request.montantCloture());
        session.setFermeeLe(LocalDateTime.now());
        session.setFermeePar(trouverUtilisateurActif(username));
        session.setStatut(StatutSessionCaisse.FERMEE);
        SessionCaisse saved = sessionCaisseRepository.save(session);
        BigDecimal ecart = request.montantCloture().subtract(toResponse(saved).soldeTheorique());
        journalActiviteService.enregistrer("CLOTURE", "SESSION_CAISSE", saved.getId(),
            "Clôture de journée, solde compté " + saved.getMontantCloture()
                + ", solde théorique " + toResponse(saved).soldeTheorique() + ", écart " + ecart, username);
        return toResponse(saved);
    }

    private SessionCaisse trouver(Long id) {
        return sessionCaisseRepository.findById(id)
            .orElseThrow(() -> new RessourceIntrouvableException("Session de caisse introuvable: " + id));
    }

    private Utilisateur trouverUtilisateurActif(String username) {
        Utilisateur utilisateur = utilisateurRepository.findByUsername(username)
            .orElseThrow(() -> new RessourceIntrouvableException("Utilisateur connecté introuvable"));
        if (!utilisateur.isActif()) {
            throw new ConflitRessourceException("Un utilisateur désactivé ne peut pas gérer une session de caisse");
        }
        return utilisateur;
    }

    private SessionCaisseResponse toResponse(SessionCaisse session) {
        BigDecimal encaissements = valeurOuZero(venteRepository.encaissementsEspecesSession(
            session.getId(), com.gesstock.gesstock.entities.StatutVente.VALIDEE, ModeReglement.ESPECES));
        BigDecimal reglementsEspeces = valeurOuZero(reglementRepository.totalParSessionEtMode(
            session.getId(), ModeReglement.ESPECES));
        BigDecimal sorties = valeurOuZero(mouvementRepository.totalSorties(session.getId()));
        BigDecimal soldeTheorique = session.getFondDeCaisse().add(encaissements)
            .add(reglementsEspeces).subtract(sorties);
        BigDecimal ecart = session.getMontantCloture() == null
            ? null : session.getMontantCloture().subtract(soldeTheorique);
        return new SessionCaisseResponse(
            session.getId(), session.getCode(), session.getJournee(), session.getOuverteLe(),
            session.getFermeeLe(), session.getFondDeCaisse(), session.getMontantCloture(),
            encaissements, reglementsEspeces, sorties, soldeTheorique, ecart, session.getStatut(),
            session.getOuvertePar().getId(), session.getOuvertePar().getUsername(),
            session.getFermeePar() == null ? null : session.getFermeePar().getId(),
            session.getFermeePar() == null ? null : session.getFermeePar().getUsername(),
            session.getStatut() == StatutSessionCaisse.OUVERTE && session.getJournee().isBefore(LocalDate.now())
        );
    }

    private BigDecimal valeurOuZero(Number value) {
        return value == null ? BigDecimal.ZERO : new BigDecimal(value.toString());
    }

    private BigDecimal valeurOuZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
