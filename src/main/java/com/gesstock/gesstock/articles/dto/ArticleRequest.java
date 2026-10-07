package com.gesstock.gesstock.articles.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ArticleRequest(
    @NotBlank @Size(max = 60) String reference,
    @NotBlank @Size(max = 150) String designation,
    @NotNull @DecimalMin("0.0") BigDecimal prixAchat,
    @NotNull @DecimalMin("0.0") BigDecimal prixVente,
    @NotNull @Min(0) Integer seuilAlerteStock,
    @Min(0) Integer quantiteStock,
    Long categorieId,
    Long uniteMesureId
) {}
