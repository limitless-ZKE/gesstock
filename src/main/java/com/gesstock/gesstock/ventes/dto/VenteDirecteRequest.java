package com.gesstock.gesstock.ventes.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import com.gesstock.gesstock.entities.ModeReglement;

import java.math.BigDecimal;
import java.util.List;

public record VenteDirecteRequest(
    Long clientId,
    boolean aCredit,
    @NotEmpty List<@Valid LigneDocumentRequest> lignes,
    @DecimalMin("0.0") @DecimalMax("100.0") @Digits(integer = 3, fraction = 4) BigDecimal tauxTva,
    ModeReglement modeReglement,
    @DecimalMin("0.0") @Digits(integer = 15, fraction = 4) BigDecimal montantRecu
) {}
