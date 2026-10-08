package com.gesstock.gesstock.inventaires.dto;

import com.gesstock.gesstock.entities.PerimetreInventaire;
import com.gesstock.gesstock.entities.StatutInventaire;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @param valeurEcartTotal somme algébrique des écarts (comptés seulement) au coût moyen figé à l'ouverture :
 *                         négative = manque, positive = surplus
 */
public record InventaireResponse(
    Long id,
    String code,
    StatutInventaire statut,
    PerimetreInventaire perimetre,
    Long categorieId,
    String categorie,
    String commentaire,
    LocalDateTime ouvertLe,
    String ouvertPar,
    LocalDateTime soumisLe,
    LocalDateTime valideLe,
    String validePar,
    long nombreLignes,
    long nombreComptees,
    long nombreEcarts,
    BigDecimal valeurEcartTotal
) {}
