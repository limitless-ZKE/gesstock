package com.gesstock.gesstock.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Freine les attaques par essais successifs de mots de passe : après {@code maxEchecs} échecs dans une fenêtre,
 * le couple (identifiant, adresse IP) est bloqué pendant {@code blocage}. Un succès remet le compteur à zéro.
 *
 * <p>Le blocage vise le COUPLE identifiant + IP, pas l'identifiant seul : sinon n'importe qui pourrait verrouiller le
 * compte de l'administrateur en le visant exprès. L'état est en mémoire (une seule instance du serveur, ce qui est le
 * cas de GesStock) ; avec plusieurs instances il faudrait le déplacer en base ou dans Redis.
 */
@Component
public class LimiteurConnexions {

    private record Etat(int echecs, Instant debutFenetre, Instant bloqueJusqua) {}

    private final Clock horloge;
    private final int maxEchecs;
    private final Duration fenetre;
    private final Duration blocage;
    private final ConcurrentHashMap<String, Etat> etats = new ConcurrentHashMap<>();

    public LimiteurConnexions(
        Clock horloge,
        @Value("${app.security.login.max-echecs:5}") int maxEchecs,
        @Value("${app.security.login.fenetre:15m}") Duration fenetre,
        @Value("${app.security.login.blocage:15m}") Duration blocage
    ) {
        this.horloge = horloge;
        this.maxEchecs = maxEchecs;
        this.fenetre = fenetre;
        this.blocage = blocage;
    }

    public static String cle(String identifiant, String adresseIp) {
        return (identifiant == null ? "" : identifiant.trim().toLowerCase(Locale.ROOT)) + "|" + adresseIp;
    }

    /** Durée restante du blocage, ou {@link Duration#ZERO} si la connexion est autorisée. */
    public Duration blocageRestant(String cle) {
        Etat etat = etats.get(cle);
        if (etat == null || etat.bloqueJusqua() == null) {
            return Duration.ZERO;
        }
        Duration restant = Duration.between(horloge.instant(), etat.bloqueJusqua());
        return restant.isNegative() || restant.isZero() ? Duration.ZERO : restant;
    }

    public void enregistrerEchec(String cle) {
        Instant maintenant = horloge.instant();
        etats.merge(cle, new Etat(1, maintenant, null), (ancien, nouveau) -> {
            boolean fenetreExpiree = maintenant.isAfter(ancien.debutFenetre().plus(fenetre));
            boolean blocageTermine = ancien.bloqueJusqua() != null && !maintenant.isBefore(ancien.bloqueJusqua());
            if (fenetreExpiree || blocageTermine) {
                return new Etat(1, maintenant, null);
            }
            int echecs = ancien.echecs() + 1;
            return new Etat(echecs, ancien.debutFenetre(), echecs >= maxEchecs ? maintenant.plus(blocage) : ancien.bloqueJusqua());
        });
        purger(maintenant);
    }

    public void reinitialiser(String cle) {
        etats.remove(cle);
    }

    /** Évite qu'une attaque qui varie les identifiants fasse grossir la mémoire sans limite. */
    private void purger(Instant maintenant) {
        if (etats.size() > 10_000) {
            etats.entrySet().removeIf(entree -> {
                Etat etat = entree.getValue();
                boolean fenetreExpiree = maintenant.isAfter(etat.debutFenetre().plus(fenetre));
                boolean blocageTermine = etat.bloqueJusqua() == null || !maintenant.isBefore(etat.bloqueJusqua());
                return fenetreExpiree && blocageTermine;
            });
        }
    }
}
