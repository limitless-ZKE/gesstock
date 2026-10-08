package com.gesstock.gesstock.inventaires.dto;

import com.gesstock.gesstock.entities.MotifAjustementStock;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Comptage d'un article. Le motif est exigé à la soumission pour tout écart (et un commentaire pour le motif « Autre »).
 */
public record LigneInventaireRequest(
    @NotNull @Min(0) Integer stockCompte,
    MotifAjustementStock motif,
    @Size(max = 300) String commentaire
) {}
