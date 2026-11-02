package com.gesstock.gesstock.articles.dto;

import com.gesstock.gesstock.entities.MotifAjustementStock;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Correction du stock d'un article après constat (casse, vol, erreur…).
 *
 * @param stockAffiche  le stock que la personne avait sous les yeux : si une vente l'a fait bouger entre-temps,
 *                      l'ajustement est refusé plutôt que d'écraser cette vente
 * @param nouveauStock  le stock réel constaté (3 décimales au plus)
 * @param commentaire   obligatoire quand le motif est « Autre »
 */
public record AjustementStockRequest(
    @NotNull @DecimalMin("0.0") BigDecimal stockAffiche,
    @NotNull @DecimalMin("0.0") @Digits(integer = 12, fraction = 3) BigDecimal nouveauStock,
    @NotNull MotifAjustementStock motif,
    @Size(max = 300) String commentaire
) {}
