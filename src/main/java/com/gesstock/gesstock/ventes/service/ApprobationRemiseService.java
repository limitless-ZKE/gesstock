package com.gesstock.gesstock.ventes.service;

import com.gesstock.gesstock.common.exception.ApprobationRefuseeException;
import com.gesstock.gesstock.entities.Role;
import com.gesstock.gesstock.entities.Utilisateur;
import com.gesstock.gesstock.repositories.UtilisateurRepository;
import com.gesstock.gesstock.security.LimiteurConnexions;
import com.gesstock.gesstock.ventes.dto.ApprobationRequest;
import com.gesstock.gesstock.ventes.politique.PolitiqueRemise;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Vérifie l'accord d'un responsable donné au comptoir : identifiant et mot de passe, sans ouvrir de session.
 *
 * <p>Comme la connexion, cette vérification est un oracle de mots de passe : elle est donc limitée en essais (le même
 * limiteur que la connexion, par vendeur) et répond toujours le même message, que le compte n'existe pas, que le mot de
 * passe soit faux ou que le compte ne soit pas un responsable. Un mot de passe est comparé même pour un compte inconnu,
 * pour que le temps de réponse ne révèle pas son existence.
 */
@Service
public class ApprobationRemiseService {

    private final UtilisateurRepository utilisateurRepository;
    private final PasswordEncoder passwordEncoder;
    private final LimiteurConnexions limiteur;
    private final PolitiqueRemise politique;
    private final String empreinteFactice;

    public ApprobationRemiseService(
        UtilisateurRepository utilisateurRepository,
        PasswordEncoder passwordEncoder,
        LimiteurConnexions limiteur,
        PolitiqueRemise politique
    ) {
        this.utilisateurRepository = utilisateurRepository;
        this.passwordEncoder = passwordEncoder;
        this.limiteur = limiteur;
        this.politique = politique;
        this.empreinteFactice = passwordEncoder.encode("empreinte-factice-pour-egaliser-les-temps");
    }

    @Transactional(readOnly = true)
    public Utilisateur verifier(String demandeur, ApprobationRequest approbation) {
        String cle = LimiteurConnexions.cle("approbation:" + demandeur, "-");
        Duration blocage = limiteur.blocageRestant(cle);
        if (!blocage.isZero()) {
            long minutes = Math.max(1, (blocage.toSeconds() + 59) / 60);
            throw new ApprobationRefuseeException("Trop d'accords refusés : réessayez dans " + minutes + " minute(s)");
        }

        Optional<Utilisateur> candidat = utilisateurRepository.findByUsername(approbation.username().trim())
            .filter(Utilisateur::isActif);
        boolean motDePasseValable = passwordEncoder.matches(approbation.motDePasse(),
            candidat.map(Utilisateur::getPassword).orElse(empreinteFactice));
        List<String> roles = candidat.map(u -> u.getRoles().stream().map(Role::getName).toList()).orElse(List.of());
        if (candidat.isEmpty() || !motDePasseValable
            || !politique.accordValable(demandeur, candidat.get().getUsername(), roles)) {
            limiteur.enregistrerEchec(cle);
            throw new ApprobationRefuseeException("Accord refusé : identifiant ou mot de passe incorrect, ou ce compte n'est pas "
                + "un responsable (MANAGER ou ADMIN) différent du vendeur");
        }
        limiteur.reinitialiser(cle);
        return candidat.get();
    }
}
