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
 * Tout est en {@link BigDecimal}, échelle 4, arrondi HALF_UP — comme les montants du reste de l'application. Les quantités
 * ont 3 décimales au plus (voir {@link Quantites}).
 */
public final class CoutMoyenPondere {

    private static final int ECHELLE = 4;

    private CoutMoyenPondere() {}

    /** CMP après une entrée en stock (réception d'un bon, retour client, ajustement positif valorisé). */
    public static BigDecimal apresEntree(BigDecimal stockAvant, BigDecimal cmpAvant, BigDecimal quantiteEntree, BigDecimal coutEntree) {
        if (quantiteEntree.signum() <= 0) {
            throw new IllegalArgumentException("La quantité entrée doit être positive");
        }
        if (coutEntree.signum() < 0) {
            throw new IllegalArgumentException("Le coût d'entrée ne peut pas être négatif");
        }
        if (stockAvant.signum() <= 0) {
            // stock vide : plus rien à pondérer, le coût de la nouvelle entrée devient le CMP
            return coutEntree.setScale(ECHELLE, RoundingMode.HALF_UP);
        }
        BigDecimal valeurAvant = cmpAvant.multiply(stockAvant);
        BigDecimal valeurEntree = coutEntree.multiply(quantiteEntree);
        return valeurAvant.add(valeurEntree).divide(stockAvant.add(quantiteEntree), ECHELLE, RoundingMode.HALF_UP);
    }

    /**
     * CMP après une sortie valorisée à un coût précis (retour au fournisseur, correction d'un bon déjà réceptionné) : on
     * retire {@code quantiteSortie} unités valorisées à {@code coutOrigine}. Si le résultat n'a pas de sens (stock épuisé ou
     * valeur négative), le CMP précédent est conservé.
     */
    public static BigDecimal apresRetourFournisseur(
        BigDecimal stockAvant, BigDecimal cmpAvant, BigDecimal quantiteSortie, BigDecimal coutOrigine
    ) {
        if (quantiteSortie.signum() <= 0 || quantiteSortie.compareTo(stockAvant) > 0) {
            throw new IllegalArgumentException("Quantité retournée invalide : " + Quantites.texte(quantiteSortie)
                + " pour un stock de " + Quantites.texte(stockAvant));
        }
        BigDecimal stockApres = stockAvant.subtract(quantiteSortie);
        if (stockApres.signum() == 0) {
            return cmpAvant;
        }
        BigDecimal valeurApres = cmpAvant.multiply(stockAvant).subtract(coutOrigine.multiply(quantiteSortie));
        if (valeurApres.signum() < 0) {
            return cmpAvant;
        }
        return valeurApres.divide(stockApres, ECHELLE, RoundingMode.HALF_UP);
    }

    /** Valeur d'un stock : quantité × CMP. */
    public static BigDecimal valeurDuStock(BigDecimal quantite, BigDecimal cmp) {
        return cmp.multiply(quantite).setScale(ECHELLE, RoundingMode.HALF_UP);
    }

    /** Marge brute d'une ligne : montant net de la ligne - coût mémorisé × quantité. Le coût ne change jamais après la vente. */
    public static BigDecimal marge(BigDecimal montantNet, BigDecimal coutUnitaireMemorise, BigDecimal quantite) {
        return montantNet.subtract(Quantites.montant(coutUnitaireMemorise, quantite));
    }
}
