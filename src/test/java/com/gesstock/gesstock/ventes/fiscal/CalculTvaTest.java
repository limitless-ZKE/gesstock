package com.gesstock.gesstock.ventes.fiscal;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CalculTvaTest {

    private static BigDecimal montant(String valeur) {
        return new BigDecimal(valeur);
    }

    @Test
    void sansTvaToutEstEgalAuNetQuelQueSoitLeRegime() {
        for (boolean ttc : new boolean[] {false, true}) {
            CalculTva.Montants montants = new CalculTva(BigDecimal.ZERO, ttc).decomposer(montant("490000"));

            assertThat(montants.montantHT()).isEqualByComparingTo("490000");
            assertThat(montants.montantTva()).isEqualByComparingTo("0");
            assertThat(montants.montantTotal()).isEqualByComparingTo("490000");
        }
    }

    @Test
    void prixHorsTaxeLaTvaSAjouteAuNet() {
        CalculTva.Montants montants = new CalculTva(montant("18"), false).decomposer(montant("490000"));

        assertThat(montants.montantHT()).isEqualByComparingTo("490000");
        assertThat(montants.montantTva()).isEqualByComparingTo("88200");
        assertThat(montants.montantTotal()).isEqualByComparingTo("578200");
    }

    @Test
    void prixToutesTaxesComprisesLaTvaEstExtraiteEtLeClientPayeLeNet() {
        CalculTva.Montants montants = new CalculTva(montant("18"), true).decomposer(montant("490000"));

        assertThat(montants.montantTotal()).isEqualByComparingTo("490000");
        assertThat(montants.montantHT()).isEqualByComparingTo("415254.2373");
        assertThat(montants.montantTva()).isEqualByComparingTo("74745.7627");
        assertThat(montants.montantHT().add(montants.montantTva())).as("HT + TVA = total, au franc près").isEqualByComparingTo("490000");
    }

    @Test
    void htPlusTvaEgaleToujoursLeTotalMemeAvecDesMontantsQuiNeTombentPasJuste() {
        for (String net : new String[] {"1", "99.99", "1300", "31000", "245000.5"}) {
            for (boolean ttc : new boolean[] {false, true}) {
                CalculTva.Montants montants = new CalculTva(montant("18"), ttc).decomposer(montant(net));

                assertThat(montants.montantHT().add(montants.montantTva()))
                    .as("HT + TVA pour %s (ttc=%s)", net, ttc).isEqualByComparingTo(montants.montantTotal());
            }
        }
    }

    @Test
    void unTauxAbsurdeEstRefuse() {
        assertThatThrownBy(() -> new CalculTva(montant("-1"), false)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CalculTva(montant("100.01"), false)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CalculTva(null, false)).isInstanceOf(IllegalArgumentException.class);
    }
}
