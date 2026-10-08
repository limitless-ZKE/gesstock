package com.gesstock.gesstock.ventes.politique;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PolitiqueRemiseTest {

    private static final BigDecimal CATALOGUE = new BigDecimal("245000");
    private static final BigDecimal COUT = new BigDecimal("189000");

    private final PolitiqueRemise politique = new PolitiqueRemise(Map.of(
        "CAISSIER", new BigDecimal("5"),
        "GESTIONNAIRE", new BigDecimal("10"),
        "MANAGER", new BigDecimal("25"),
        "ADMIN", new BigDecimal("100")), BigDecimal.ZERO);

    private PolitiqueRemise.Decision decision(List<String> roles, String prixNegocie) {
        BigDecimal negocie = new BigDecimal(prixNegocie);
        return politique.evaluer(roles, CATALOGUE, negocie, negocie, COUT);
    }

    @Test
    void uneRemiseSousLePlafondDuCaissierPasseSansAccord() {
        // 5 % de 245 000 = 12 250 -> 232 750 est EXACTEMENT au plafond : autorisé
        assertThat(decision(List.of("CAISSIER"), "232750").approbationRequise()).isFalse();
        assertThat(decision(List.of("CAISSIER"), "240000").approbationRequise()).isFalse();
        assertThat(decision(List.of("CAISSIER"), "245000").approbationRequise()).isFalse();
    }

    @Test
    void auDelaDuPlafondUnAccordEstExigeAuCentimePres() {
        var decision = decision(List.of("CAISSIER"), "232749.99");

        assertThat(decision.approbationRequise()).isTrue();
        assertThat(decision.motif()).contains("au-delà du plafond autorisé de 5 %");
    }

    @Test
    void lePlafondDependDuRoleEtLePlusFavorableGagne() {
        assertThat(decision(List.of("GESTIONNAIRE"), "220500").approbationRequise()).isFalse();   // 10 %
        assertThat(decision(List.of("GESTIONNAIRE"), "220000").approbationRequise()).isTrue();    // 10,2 %
        assertThat(decision(List.of("CAISSIER", "MANAGER"), "190000").approbationRequise()).isFalse(); // 22,4 % <= 25 %
        assertThat(decision(List.of("ROLE_MANAGER"), "180000").approbationRequise()).isTrue();    // 26,5 % > 25 %
    }

    @Test
    void unRoleInconnuOuAbsentNAPasLeDroitDeRemise() {
        assertThat(decision(List.of("VENDEUR"), "244999").approbationRequise()).isTrue();
        assertThat(decision(List.of(), "244999").approbationRequise()).isTrue();
        assertThat(decision(List.of(), "245000").approbationRequise()).isFalse(); // aucune remise : rien à approuver
    }

    @Test
    void onNeVendJamaisSousLeCoutSansAccordMemeSousLePlafond() {
        // l'administrateur a un plafond de 100 %, mais vendre à 100 000 alors que le coût est 189 000 reste signalé
        var decision = decision(List.of("ADMIN"), "100000");

        assertThat(decision.approbationRequise()).isTrue();
        assertThat(decision.motif()).contains("inférieur au coût");
    }

    @Test
    void laMargePlancherRelèveLeSeuilDeVenteMinimal() {
        var avecMarge = new PolitiqueRemise(Map.of("ADMIN", new BigDecimal("100")), new BigDecimal("10")); // coût + 10 %
        BigDecimal seuil = new BigDecimal("207900"); // 189 000 × 1,10

        assertThat(avecMarge.evaluer(List.of("ADMIN"), CATALOGUE, seuil, seuil, COUT).approbationRequise()).isFalse();
        BigDecimal juste = seuil.subtract(new BigDecimal("0.01"));
        assertThat(avecMarge.evaluer(List.of("ADMIN"), CATALOGUE, juste, juste, COUT).approbationRequise()).isTrue();
    }

    @Test
    void laPromotionNeCompteQueDansLeControleDuCout() {
        // le caissier ne négocie rien (245 000) ; une promotion de 10 % ramène le prix à 220 500 : pas d'accord requis
        var decision = politique.evaluer(List.of("CAISSIER"), CATALOGUE, CATALOGUE, new BigDecimal("220500"), COUT);

        assertThat(decision.approbationRequise()).isFalse();
    }

    @Test
    void sansCoutConnuSeulLePlafondS_applique() {
        var decision = politique.evaluer(List.of("ADMIN"), CATALOGUE, new BigDecimal("1"), new BigDecimal("1"), null);

        assertThat(decision.approbationRequise()).isFalse();
    }

    @Test
    void unAccordNEstValableQueDUneAutrePersonneAvecUnRoleDApprobation() {
        assertThat(politique.accordValable("caissier1", "manager1", List.of("MANAGER"))).isTrue();
        assertThat(politique.accordValable("caissier1", "admin", List.of("ROLE_ADMIN"))).isTrue();
        assertThat(politique.accordValable("manager1", "manager1", List.of("MANAGER"))).as("on ne s'approuve pas soi-même").isFalse();
        assertThat(politique.accordValable("caissier1", "caissier2", List.of("CAISSIER"))).as("un caissier n'approuve pas").isFalse();
        assertThat(politique.accordValable("caissier1", "gest1", List.of("GESTIONNAIRE"))).isFalse();
    }
}
