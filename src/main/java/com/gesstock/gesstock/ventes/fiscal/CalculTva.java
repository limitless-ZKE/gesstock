package com.gesstock.gesstock.ventes.fiscal;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Décomposition d'un montant de vente en hors taxes, TVA et total, selon le régime fixé par le SERVEUR
 * (jamais par le caissier : un taux saisi à la caisse permettrait de ne pas facturer la taxe).
 *
 * <ul>
 *   <li>prix catalogue HT : la TVA s'AJOUTE au net des lignes (total = HT + TVA) ;</li>
 *   <li>prix catalogue TTC : la TVA est EXTRAITE du net (HT = net / (1 + taux), le client paie le net).</li>
 * </ul>
 * Échelle 4 et arrondi HALF_UP, comme tous les montants de l'application. Avec un taux de 0 % (réglage par défaut
 * tant que la TVA n'est pas activée), les deux régimes donnent le même résultat : HT = total, TVA = 0.
 */
public record CalculTva(BigDecimal taux, boolean prixTtc) {

    private static final int ECHELLE = 4;
    private static final BigDecimal CENT = BigDecimal.valueOf(100);

    public CalculTva {
        if (taux == null || taux.signum() < 0 || taux.compareTo(CENT) > 0) {
            throw new IllegalArgumentException("Le taux de TVA doit être compris entre 0 et 100 : " + taux);
        }
    }

    public record Montants(BigDecimal montantHT, BigDecimal montantTva, BigDecimal montantTotal) {}

    public Montants decomposer(BigDecimal net) {
        if (taux.signum() == 0) {
            return new Montants(net, BigDecimal.ZERO.setScale(ECHELLE), net);
        }
        if (prixTtc) {
            BigDecimal ht = net.divide(BigDecimal.ONE.add(taux.divide(CENT, 8, RoundingMode.HALF_UP)), ECHELLE, RoundingMode.HALF_UP);
            return new Montants(ht, net.subtract(ht), net);
        }
        BigDecimal tva = net.multiply(taux).divide(CENT, ECHELLE, RoundingMode.HALF_UP);
        return new Montants(net, tva, net.add(tva));
    }
}
