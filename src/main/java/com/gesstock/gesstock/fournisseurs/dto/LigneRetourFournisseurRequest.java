package com.gesstock.gesstock.fournisseurs.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Quantité rendue sur UNE ligne du bon d'origine (le coût est celui de cette ligne). */
public record LigneRetourFournisseurRequest(
    @NotNull Long ligneApprovisionnementId,
    @NotNull @Min(1) Integer quantite
) {}
