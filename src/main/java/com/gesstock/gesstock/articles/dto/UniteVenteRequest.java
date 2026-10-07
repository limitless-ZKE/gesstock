package com.gesstock.gesstock.articles.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record UniteVenteRequest(
    @Size(max = 30) String code,
    @NotNull Long uniteMesureId,
    @NotNull @DecimalMin("0.000001") BigDecimal facteurConversion,
    @DecimalMin("0.0") BigDecimal prixVente,
    Boolean active
) {}
