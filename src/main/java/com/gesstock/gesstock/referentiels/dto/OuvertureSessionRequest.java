package com.gesstock.gesstock.referentiels.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record OuvertureSessionRequest(
    LocalDate journee,
    @NotNull @DecimalMin("0.0") BigDecimal fondDeCaisse
) {}
