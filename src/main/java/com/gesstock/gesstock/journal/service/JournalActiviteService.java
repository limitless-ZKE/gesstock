package com.gesstock.gesstock.journal.service;

import com.gesstock.gesstock.entities.JournalActivite;
import com.gesstock.gesstock.entities.SessionCaisse;
import com.gesstock.gesstock.entities.StatutVente;
import com.gesstock.gesstock.entities.Utilisateur;
import com.gesstock.gesstock.journal.dto.JournalActiviteResponse;
import com.gesstock.gesstock.journal.dto.RecapitulatifClotureResponse;
import com.gesstock.gesstock.repositories.JournalActiviteRepository;
import com.gesstock.gesstock.repositories.SessionCaisseRepository;
import com.gesstock.gesstock.repositories.UtilisateurRepository;
import com.gesstock.gesstock.repositories.VenteRepository;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class JournalActiviteService {

    private final JournalActiviteRepository journalRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final SessionCaisseRepository sessionCaisseRepository;
    private final VenteRepository venteRepository;

    public JournalActiviteService(
        JournalActiviteRepository journalRepository,
        UtilisateurRepository utilisateurRepository,
        SessionCaisseRepository sessionCaisseRepository,
        VenteRepository venteRepository
    ) {
        this.journalRepository = journalRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.sessionCaisseRepository = sessionCaisseRepository;
        this.venteRepository = venteRepository;
    }

    /** @param username l'auteur de l'opération ; nul : le compte connecté (aucun, par exemple au démarrage : « système ») */
    public void enregistrer(String action, String entite, Long entiteId, String details, String username) {
        JournalActivite activite = new JournalActivite();
        activite.setAction(action);
        activite.setEntite(entite);
        activite.setEntiteId(entiteId);
        activite.setDetails(tronquer(details, 2000));
        String auteur = username != null && !username.isBlank() ? username : compteConnecte();
        if (auteur != null) {
            utilisateurRepository.findByUsername(auteur).ifPresent(activite::setUtilisateur);
        }
        journalRepository.save(activite);
    }

    /**
     * Une connexion réussie ou refusée, avec le poste d'où elle vient. Le journal la rattache au compte visé s'il existe : un
     * responsable voit ainsi les essais répétés sur le compte d'un employé.
     *
     * @param raison pour un refus : « mot de passe incorrect », « compte désactivé »… (nul pour une connexion réussie)
     */
    public void enregistrerConnexion(String identifiant, String adresseIp, String raison) {
        String nom = identifiant == null ? "" : identifiant.trim();
        Optional<Utilisateur> compte = nom.isEmpty() ? Optional.empty() : utilisateurRepository.findByUsernameIgnoreCase(nom);
        JournalActivite activite = new JournalActivite();
        activite.setAction(raison == null ? "CONNEXION" : "CONNEXION_REFUSEE");
        activite.setEntite("COMPTE");
        activite.setAdresseIp(tronquer(adresseIp, 64));
        compte.ifPresent(utilisateur -> {
            activite.setUtilisateur(utilisateur);
            activite.setEntiteId(utilisateur.getId());
        });
        String poste = adresseIp == null || adresseIp.isBlank() ? "" : ", depuis le poste " + adresseIp;
        String compteVise = compte.map(utilisateur -> "compte " + utilisateur.getUsername())
            .orElse("identifiant inconnu « " + tronquer(nom, 80) + " »");
        activite.setDetails(raison == null
            ? "Connexion du " + compteVise + poste
            : "Connexion refusée pour " + (compte.isPresent() ? "le " : "l'") + compteVise + " (" + raison + ")" + poste);
        journalRepository.save(activite);
    }

    private static String compteConnecte() {
        Authentication authentification = SecurityContextHolder.getContext().getAuthentication();
        if (authentification == null || !authentification.isAuthenticated() || authentification instanceof AnonymousAuthenticationToken) {
            return null;
        }
        return authentification.getName();
    }

    private static String tronquer(String texte, int longueur) {
        return texte == null || texte.length() <= longueur ? texte : texte.substring(0, longueur);
    }

    @Transactional(readOnly = true)
    public List<JournalActiviteResponse> listerJournee(LocalDate journee) {
        LocalDate jour = journee == null ? LocalDate.now() : journee;
        return journalRepository.findByDateActiviteBetweenOrderByDateActiviteAsc(
                jour.atStartOfDay(), jour.plusDays(1).atStartOfDay())
            .stream().map(JournalActiviteService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public RecapitulatifClotureResponse recapitulatif(Long sessionId) {
        SessionCaisse session = sessionCaisseRepository.findById(sessionId)
            .orElseThrow(() -> new com.gesstock.gesstock.common.exception.RessourceIntrouvableException("Session de caisse introuvable: " + sessionId));
        LocalDateTime debut = session.getOuverteLe();
        // L'entrée « CLOTURE » est journalisée juste après fermeeLe : on garde une marge pour l'inclure.
        LocalDateTime fin = session.getFermeeLe() == null
            ? LocalDateTime.now() : session.getFermeeLe().plusSeconds(1);
        List<JournalActiviteResponse> activites = journalRepository
            .findByDateActiviteBetweenOrderByDateActiviteAsc(debut, fin)
            .stream().map(JournalActiviteService::toResponse).toList();
        return new RecapitulatifClotureResponse(
            session.getId(),
            session.getJournee(),
            session.getStatut(),
            session.getFondDeCaisse(),
            session.getMontantCloture(),
            venteRepository.compterVentesSession(sessionId, StatutVente.VALIDEE),
            valeurOuZero(venteRepository.chiffreAffairesSession(sessionId, StatutVente.VALIDEE)),
            activites.size(),
            session.getOuverteLe(),
            session.getFermeeLe(),
            activites
        );
    }

    private static JournalActiviteResponse toResponse(JournalActivite activite) {
        Utilisateur utilisateur = activite.getUtilisateur();
        return new JournalActiviteResponse(
            activite.getId(), activite.getAction(), activite.getEntite(), activite.getEntiteId(),
            activite.getDetails(), activite.getDateActivite(),
            utilisateur == null ? null : utilisateur.getId(),
            utilisateur == null ? null : utilisateur.getUsername()
        );
    }

    private static BigDecimal valeurOuZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
