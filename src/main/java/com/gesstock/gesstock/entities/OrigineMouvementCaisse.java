package com.gesstock.gesstock.entities;

/**
 * D'où vient un mouvement de caisse. Seules les sorties {@link #MANUEL} sont des dépenses dans les statistiques : le paiement
 * d'un fournisseur est déjà compté dans les achats, et le remboursement d'une vente annulée sort du chiffre d'affaires.
 */
public enum OrigineMouvementCaisse {
    MANUEL,
    REGLEMENT_FOURNISSEUR,
    REMBOURSEMENT_CLIENT
}
