package com.gesstock.gesstock.stock;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Coût moyen pondéré (CMP) d'un article : la valeur du stock divisée par sa quantité.
 *
 * <ul>
 *   <li>une ENTRÉE (réception) recalcule le CMP ;</li>
 *   <li>une SORTIE (vente, casse) ne le change pas : elle sort au CMP du moment, qu'on mémorise sur la ligne de vente ;</li>
 *   <li>un RETOUR EN STOCK (annulation de vente) rentre au coût mémorisé à la vente, pas au CMP du jour.</li>
 * </ul>
 * Tout est en {@link BigDecimal}, échelle 4, arrondi HALF_UP — comme les montants du reste de l'application.
 */
public final class CoutMoyenPondere {

    private static final int ECHELLE = 4;

    private CoutMoyenPondere() {}

    /** CMP après une entrée en stock (réception d'un bon, retour client, ajustement positif valorisé). */
    public static BigDecimal apresEntree(int stockAvant, BigDecimal cmpAvant, int quantiteEntree, BigDecimal coutEntree) {
        if (quantiteEntree <= 0) {
            throw new IllegalArgumentException("La quantité entrée doit être positive");
        }
        if (coutEntree.signum() < 0) {
            throw new IllegalArgumentException("Le coût d'entrée ne peut pas être négatif");
        }
        if (stockAvant <= 0) {
            // stock vide : plus rien à pondérer, le coût de la nouvelle entrée devient le CMP
            return coutEntree.setScale(ECHELLE, RoundingMode.HALF_UP);
        }
        BigDecimal valeurAvant = cmpAvant.multiply(BigDecimal.valueOf(stockAvant));
        BigDecimal valeurEntree = coutEntree.multiply(BigDecimal.valueOf(quantiteEntree));
        return valeurAvant.add(valeurEntree)
            .divide(BigDecimal.valueOf((long) stockAvant + quantiteEntree), ECHELLE, RoundingMode.HALF_UP);
    }

    /**
     * CMP après un retour au fournisseur : on retire {@code quantiteRetournee} unités valorisées à leur coût d'origine.
     * Si le résultat n'a pas de sens (stock épuisé ou valeur négative), le CMP précédent est conservé.
     */
    public static BigDecimal apresRetourFournisseur(
        int stockAvant, BigDecimal cmpAvant, int quantiteRetournee, BigDecimal coutOrigine
    ) {
        if (quantiteRetournee <= 0 || quantiteRetournee > stockAvant) {
            throw new IllegalArgumentException("Quantité retournée invalide : " + quantiteRetournee + " pour un stock de " + stockAvant);
        }
        int stockApres = stockAvant - quantiteRetournee;
        if (stockApres == 0) {
            return cmpAvant;
        }
        BigDecimal valeurApres = cmpAvant.multiply(BigDecimal.valueOf(stockAvant))
            .subtract(coutOrigine.multiply(BigDecimal.valueOf(quantiteRetournee)));
        if (valeurApres.signum() < 0) {
            return cmpAvant;
        }
        return valeurApres.divide(BigDecimal.valueOf(stockApres), ECHELLE, RoundingMode.HALF_UP);
    }

    /** Valeur d'un stock : quantité × CMP. */
    public static BigDecimal valeurDuStock(int quantite, BigDecimal cmp) {
        return cmp.multiply(BigDecimal.valueOf(quantite)).setScale(ECHELLE, RoundingMode.HALF_UP);
    }

    /** Marge brute d'une ligne : (prix net - coût mémorisé) × quantité. Le coût ne change jamais après la vente. */
    public static BigDecimal marge(BigDecimal prixNetUnitaire, BigDecimal coutUnitaireMemorise, int quantite) {
        return prixNetUnitaire.subtract(coutUnitaireMemorise).multiply(BigDecimal.valueOf(quantite));
    }
}
