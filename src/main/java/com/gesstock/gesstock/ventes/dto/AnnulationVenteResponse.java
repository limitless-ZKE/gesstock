package com.gesstock.gesstock.ventes.dto;

import java.math.BigDecimal;

/**
 * @param montantRembourse        total des versements rendus au client
 * @param remboursementEspeces    part rendue en espèces (sortie de la caisse du jour)
 * @param remboursementAutresModes part rendue par le même moyen que le versement (carte, virement, etc.)
 */
public record AnnulationVenteResponse(
    VenteResponse vente,
    BigDecimal montantRembourse,
    BigDecimal remboursementEspeces,
    BigDecimal remboursementAutresModes
) {}
