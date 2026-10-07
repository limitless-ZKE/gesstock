package com.gesstock.gesstock.referentiels.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ClotureSessionRequest(
    @NotNull @DecimalMin("0.0") BigDecimal montantCloture
) {}
