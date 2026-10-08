package com.gesstock.gesstock.ventes.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * @param approbation accord d'un responsable, à fournir quand une remise négociée dépasse le plafond du vendeur ou qu'un
 *                    prix passe sous le coût
 */
public record ProformaRequest(
    @NotBlank @Size(max = 50) String numero,
    LocalDate dateEmission,
    LocalDate dateExpiration,
    Long clientId,
    @NotEmpty List<@Valid LigneDocumentRequest> lignes,
    @Valid ApprobationRequest approbation
) {
    /** Sans accord de responsable : la forme courante, tant que le proforma reste dans les limites du vendeur. */
    public ProformaRequest(
        String numero, LocalDate dateEmission, LocalDate dateExpiration, Long clientId, List<LigneDocumentRequest> lignes
    ) {
        this(numero, dateEmission, dateExpiration, clientId, lignes, null);
    }
}
