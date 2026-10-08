package com.gesstock.gesstock.fournisseurs.dto;

import com.gesstock.gesstock.entities.ModeReglement;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Paiement d'une dette fournisseur. En espèces, il sort de la caisse (session ouverte et solde suffisant exigés). */
public record ReglementFournisseurRequest(
    @NotNull @DecimalMin("0.0001") @Digits(integer = 15, fraction = 4) BigDecimal montant,
    @NotNull ModeReglement modeReglement,
    @Size(max = 80) String reference
) {}
