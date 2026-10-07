package com.gesstock.gesstock.ventes.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * @param prixUnitaire prix négocié avec le client (facultatif, deux décimales au plus). Il ne peut pas dépasser
 *                     le prix catalogue : l'écart est la remise négociée, affichée sur les documents.
 */
public record LigneDocumentRequest(
    @NotNull Long articleId,
    Long uniteVenteId,
    @NotNull @Min(1) Integer quantite,
    @DecimalMin("0.0") @Digits(integer = 15, fraction = 2) BigDecimal prixUnitaire,
    Long promotionId
) {}
