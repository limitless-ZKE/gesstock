package com.gesstock.gesstock.inventaires.dto;

import com.gesstock.gesstock.entities.MotifAjustementStock;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Comptage d'un article (3 décimales au plus). Le motif est exigé à la soumission pour tout écart (et un commentaire pour
 * le motif « Autre »).
 */
public record LigneInventaireRequest(
    @NotNull @DecimalMin("0.0") @Digits(integer = 12, fraction = 3) BigDecimal stockCompte,
    MotifAjustementStock motif,
    @Size(max = 300) String commentaire
) {}
