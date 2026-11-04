package com.gesstock.gesstock.statistiques.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Ce que l'argent de la période est devenu, à côté du chiffre d'affaires (qui reste affiché tel quel).
 *
 * @param achats          total des bons d'approvisionnement réceptionnés dans la période
 * @param autresDepenses  sorties de caisse saisies à la main (le paiement d'un fournisseur est déjà dans les achats ; le
 *                        remboursement d'une vente annulée est déjà sorti du chiffre d'affaires)
 * @param totalDepenses   achats + autres dépenses
 * @param resultat        chiffre d'affaires − total des dépenses : ce que les ventes ont laissé après les dépenses
 * @param entreesDeFonds  apports d'argent dans la caisse : jamais comptés dans le chiffre d'affaires ni dans le résultat
 */
public record BilanDepensesResponse(
    BigDecimal achats,
    long nombreBonsRecus,
    BigDecimal autresDepenses,
    long nombreDepenses,
    BigDecimal totalDepenses,
    BigDecimal resultat,
    BigDecimal entreesDeFonds,
    List<AchatRapportResponse> bonsRecus,
    List<DepenseRapportResponse> depenses
) {}
