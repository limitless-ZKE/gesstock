package com.gesstock.gesstock.approvisionnements.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record LigneApprovisionnementRequest(
    @NotNull Long articleId,
    @NotNull @Min(1) Integer quantite,
    @NotNull @PositiveOrZero BigDecimal prixUnitaire
) {}
