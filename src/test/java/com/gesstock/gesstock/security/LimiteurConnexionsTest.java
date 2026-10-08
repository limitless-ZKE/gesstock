package com.gesstock.gesstock.security;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class LimiteurConnexionsTest {

    private final HorlogeReglable horloge = new HorlogeReglable();
    private final LimiteurConnexions limiteur = new LimiteurConnexions(horloge, 5, Duration.ofMinutes(15), Duration.ofMinutes(15));
    private final String cle = LimiteurConnexions.cle("Admin ", "10.0.0.1");

    @Test
    void bloqueApresLeCinquiemeEchecSeulement() {
        for (int i = 1; i <= 4; i++) {
            limiteur.enregistrerEchec(cle);
            assertThat(limiteur.blocageRestant(cle)).as("après %d échecs", i).isZero();
        }
        limiteur.enregistrerEchec(cle);

        assertThat(limiteur.blocageRestant(cle)).isEqualTo(Duration.ofMinutes(15));
    }

    @Test
    void leBlocageSeLeveAvecLeTemps() {
        for (int i = 0; i < 5; i++) {
            limiteur.enregistrerEchec(cle);
        }
        horloge.avancer(Duration.ofMinutes(10));
        assertThat(limiteur.blocageRestant(cle)).isEqualTo(Duration.ofMinutes(5));

        horloge.avancer(Duration.ofMinutes(5));
        assertThat(limiteur.blocageRestant(cle)).isZero();
    }

    @Test
    void unSuccesRemetLeCompteurAZero() {
        for (int i = 0; i < 4; i++) {
            limiteur.enregistrerEchec(cle);
        }
        limiteur.reinitialiser(cle);
        limiteur.enregistrerEchec(cle);

        assertThat(limiteur.blocageRestant(cle)).isZero(); // 1 échec seulement, pas 5
    }

    @Test
    void desEchecsEspacesAuDelaDeLaFenetreNeSeCumulentPas() {
        for (int i = 0; i < 4; i++) {
            limiteur.enregistrerEchec(cle);
        }
        horloge.avancer(Duration.ofMinutes(16)); // la fenêtre de 15 minutes est passée
        limiteur.enregistrerEchec(cle);
        limiteur.enregistrerEchec(cle);

        assertThat(limiteur.blocageRestant(cle)).isZero();
    }

    @Test
    void lAttaquantNeVerrouillePasUnCompteDepuisUneAutreAdresse() {
        for (int i = 0; i < 5; i++) {
            limiteur.enregistrerEchec(LimiteurConnexions.cle("admin", "203.0.113.9")); // l'attaquant
        }

        assertThat(limiteur.blocageRestant(LimiteurConnexions.cle("admin", "203.0.113.9"))).isPositive();
        assertThat(limiteur.blocageRestant(LimiteurConnexions.cle("admin", "10.0.0.1"))).as("le vrai administrateur").isZero();
    }

    @Test
    void lIdentifiantEstInsensibleALaCasseEtAuxEspaces() {
        for (int i = 0; i < 5; i++) {
            limiteur.enregistrerEchec(LimiteurConnexions.cle("ADMIN", "10.0.0.1"));
        }

        assertThat(limiteur.blocageRestant(LimiteurConnexions.cle("  admin", "10.0.0.1"))).isPositive();
    }
}
