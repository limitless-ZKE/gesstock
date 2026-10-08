package com.gesstock.gesstock.fournisseurs.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Avoir saisi à la main (ristourne, geste commercial…). Les avoirs de retour de marchandise sont créés par le retour. */
public record AvoirFournisseurRequest(
    @NotNull @DecimalMin("0.0001") @Digits(integer = 15, fraction = 4) BigDecimal montant,
    @NotBlank @Size(max = 300) String motif
) {}
