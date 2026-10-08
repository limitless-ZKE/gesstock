package com.gesstock.gesstock.ventes.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import com.gesstock.gesstock.entities.ModeReglement;

import java.math.BigDecimal;
import java.util.List;

/**
 * @param tauxTva     facultatif : le taux est imposé par le serveur, un autre taux est refusé
 * @param approbation accord d'un responsable, à fournir quand une remise dépasse le plafond du vendeur ou qu'un prix passe
 *                    sous le coût (le serveur répond alors {@code APPROBATION_REQUISE} tant qu'il manque)
 */
public record VenteDirecteRequest(
    Long clientId,
    boolean aCredit,
    @NotEmpty List<@Valid LigneDocumentRequest> lignes,
    @DecimalMin("0.0") @DecimalMax("100.0") @Digits(integer = 3, fraction = 4) BigDecimal tauxTva,
    ModeReglement modeReglement,
    @DecimalMin("0.0") @Digits(integer = 15, fraction = 4) BigDecimal montantRecu,
    @Valid ApprobationRequest approbation
) {
    /** Sans accord de responsable : la forme courante, tant que la vente reste dans les limites du vendeur. */
    public VenteDirecteRequest(
        Long clientId, boolean aCredit, List<LigneDocumentRequest> lignes, BigDecimal tauxTva,
        ModeReglement modeReglement, BigDecimal montantRecu
    ) {
        this(clientId, aCredit, lignes, tauxTva, modeReglement, montantRecu, null);
    }
}
