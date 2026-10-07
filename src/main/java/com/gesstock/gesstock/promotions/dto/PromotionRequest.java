package com.gesstock.gesstock.promotions.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

public record PromotionRequest(
    @NotBlank @Size(max = 120) String nom,
    @NotNull @DecimalMin("0.01") @DecimalMax("100.00") BigDecimal pourcentage,
    @NotNull LocalDateTime debut,
    @NotNull LocalDateTime fin,
    Boolean active,
    @NotEmpty Set<Long> articleIds
) {}
