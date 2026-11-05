package com.gesstock.gesstock.articles.importation;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Les nombres tels qu'on les écrit dans un tableur français. */
class NombreSaisiTest {

    private static void lit(String texte, String attendu) {
        assertThat(NombreSaisi.lire(texte)).as("« %s »", texte).isEqualByComparingTo(new BigDecimal(attendu));
    }

    @Test
    void lesEspacesSeparentLesMilliersEtLaVirguleOuLePointSontLaVirguleDecimale() {
        lit("1500", "1500");
        lit("1 500", "1500");
        lit("1 500", "1500");
        lit("1 500", "1500");
        lit("1500,50", "1500.5");
        lit("1500.5", "1500.5");
        lit("0,125", "0.125");
        lit("1.500,50", "1500.5");
        lit("1,500.50", "1500.5");
        lit("1.500.000", "1500000");
    }

    @Test
    void uneMonnaieApresLeNombreEstAcceptee() {
        lit("1 500 F CFA", "1500");
        lit("1500F", "1500");
        lit("1500 FCFA", "1500");
        lit("2000 XOF", "2000");
        lit("2000 francs", "2000");
    }

    @Test
    void uneCelluleVideEstNullEtUnTexteQuiNEstPasUnNombreEstRefuse() {
        assertThat(NombreSaisi.lire("")).isNull();
        assertThat(NombreSaisi.lire("   ")).isNull();
        assertThat(NombreSaisi.lire(null)).isNull();
        for (String illisible : new String[] {"abc", "12abc", "1,2,3", "-5", "1e5", "F", "1500 euros", ".", ",", "1 2 x"}) {
            assertThatThrownBy(() -> NombreSaisi.lire(illisible)).as("« %s »", illisible).isInstanceOf(NumberFormatException.class);
        }
        assertThatThrownBy(() -> NombreSaisi.lire("1234567890123")).isInstanceOf(NumberFormatException.class);
    }
}
