package com.gesstock.gesstock.fournisseurs.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/** Quantité rendue sur UNE ligne du bon d'origine (le coût est celui de cette ligne), 3 décimales au plus. */
public record LigneRetourFournisseurRequest(
    @NotNull Long ligneApprovisionnementId,
    @NotNull @Positive @Digits(integer = 12, fraction = 3) BigDecimal quantite
) {}
