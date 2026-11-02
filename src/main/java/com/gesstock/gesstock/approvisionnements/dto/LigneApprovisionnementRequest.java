package com.gesstock.gesstock.approvisionnements.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

/** @param quantite quantité reçue, en unité de stock, 3 décimales au plus */
public record LigneApprovisionnementRequest(
    @NotNull Long articleId,
    @NotNull @Positive @Digits(integer = 12, fraction = 3) BigDecimal quantite,
    @NotNull @PositiveOrZero BigDecimal prixUnitaire
) {}
