package com.gesstock.gesstock.clients.dto;

import com.gesstock.gesstock.entities.ModeReglement;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ReglementRequest(
    @NotNull @DecimalMin(value = "0.0001") @Digits(integer = 15, fraction = 4) BigDecimal montant,
    @NotNull ModeReglement modeReglement,
    @Size(max = 80) String reference
) {}
