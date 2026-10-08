package com.gesstock.gesstock.articles.dto;

import com.gesstock.gesstock.entities.MotifAjustementStock;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Correction du stock d'un article après constat (casse, vol, erreur…).
 *
 * @param stockAffiche  le stock que la personne avait sous les yeux : si une vente l'a fait bouger entre-temps,
 *                      l'ajustement est refusé plutôt que d'écraser cette vente
 * @param nouveauStock  le stock réel constaté
 * @param commentaire   obligatoire quand le motif est « Autre »
 */
public record AjustementStockRequest(
    @NotNull @Min(0) Integer stockAffiche,
    @NotNull @Min(0) Integer nouveauStock,
    @NotNull MotifAjustementStock motif,
    @Size(max = 300) String commentaire
) {}
