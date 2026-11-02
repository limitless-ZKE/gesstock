package com.gesstock.gesstock.approvisionnements.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

/**
 * @param quantite     quantité reçue, en unité de stock, 3 décimales au plus
 * @param prixUnitaire prix d'achat unitaire
 * @param prixVente    prix de revente (facultatif) : il devient le prix de vente de l'article à la réception du bon
 */
public record LigneApprovisionnementRequest(
    @NotNull Long articleId,
    @NotNull @Positive @Digits(integer = 12, fraction = 3) BigDecimal quantite,
    @NotNull @PositiveOrZero BigDecimal prixUnitaire,
    @PositiveOrZero @Digits(integer = 15, fraction = 4) BigDecimal prixVente
) {
    /** Sans prix de revente : celui de l'article ne change pas. */
    public LigneApprovisionnementRequest(Long articleId, BigDecimal quantite, BigDecimal prixUnitaire) {
        this(articleId, quantite, prixUnitaire, null);
    }
}
