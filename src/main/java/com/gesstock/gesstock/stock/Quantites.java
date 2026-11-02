package com.gesstock.gesstock.stock;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Quantités de stock et de vente : décimales, 3 chiffres après la virgule au plus (une caisse entamée, un liquide au litre).
 * Les montants qui en découlent sont arrondis à 4 décimales, comme les autres montants de l'application.
 */
public final class Quantites {

    /** Chiffres après la virgule d'une quantité (colonnes DECIMAL(15,3)). */
    public static final int ECHELLE = 3;
    private static final int ECHELLE_MONTANT = 4;

    private Quantites() {}

    /** Quantité × prix unitaire, arrondi à 4 décimales. */
    public static BigDecimal montant(BigDecimal prixUnitaire, BigDecimal quantite) {
        return prixUnitaire.multiply(quantite).setScale(ECHELLE_MONTANT, RoundingMode.HALF_UP);
    }

    /** Vrai si la quantité tient en 3 décimales (2,5 ou 0,125 oui ; 0,1255 non). */
    public static boolean precisionValide(BigDecimal quantite) {
        return quantite.stripTrailingZeros().scale() <= ECHELLE;
    }

    /** Quantité lisible dans un message ou le journal : « 2 », « 2,5 », « 0,125 ». */
    public static String texte(BigDecimal quantite) {
        if (quantite == null) {
            return "";
        }
        BigDecimal reduite = quantite.stripTrailingZeros();
        if (reduite.scale() < 0) {
            reduite = reduite.setScale(0);
        }
        return reduite.toPlainString().replace('.', ',');
    }

    public static BigDecimal de(long quantite) {
        return BigDecimal.valueOf(quantite);
    }
}
