package com.gesstock.gesstock.caisse.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record MouvementCaisseRequest(
    @NotBlank @Size(max = 500) String motif,
    @NotBlank @Size(max = 120) String beneficiaire,
    @NotNull @DecimalMin("0.0001") @Digits(integer = 15, fraction = 4) BigDecimal montant
) {}
