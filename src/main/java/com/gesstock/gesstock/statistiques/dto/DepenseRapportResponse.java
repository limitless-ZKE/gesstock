package com.gesstock.gesstock.statistiques.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Une sortie de caisse saisie à la main pendant la période : une dépense. */
public record DepenseRapportResponse(
    Long id, LocalDateTime date, String motif, String beneficiaire, String effectuePar, BigDecimal montant
) {}
