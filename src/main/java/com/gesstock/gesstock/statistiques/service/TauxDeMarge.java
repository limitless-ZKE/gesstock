package com.gesstock.gesstock.statistiques.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Taux de marge d'un article ou d'une catégorie, calculé sur le PRIX DE VENTE : sur 100 F encaissés, combien restent après le
 * coût. Vide quand le coût est inconnu (0) ou qu'il n'y a pas de vente : un taux de 100 % serait faux et trompeur.
 */
public final class TauxDeMarge {

    private TauxDeMarge() {}

    /** En pourcentage, une décimale ; {@code null} si le chiffre d'affaires ou le coût ne sont pas positifs. */
    public static BigDecimal de(BigDecimal chiffreAffaires, BigDecimal coutAchat) {
        if (chiffreAffaires == null || coutAchat == null || chiffreAffaires.signum() <= 0 || coutAchat.signum() <= 0) {
            return null;
        }
        return chiffreAffaires.subtract(coutAchat).multiply(BigDecimal.valueOf(100))
            .divide(chiffreAffaires, 1, RoundingMode.HALF_UP);
    }

    /** « 40 % », « -20,5 % » ; « — » quand il n'y a pas de taux (coût inconnu). */
    public static String texte(BigDecimal taux) {
        return taux == null ? "—" : taux.stripTrailingZeros().toPlainString().replace('.', ',') + " %";
    }
}
