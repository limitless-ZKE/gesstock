package com.gesstock.gesstock.statistiques.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SyntheseStatistiquesResponse(
    LocalDate journee,
    String mois,
    ArticleTopVentesResponse articleLePlusVendu,
    long nombreVentesJour,
    BigDecimal chiffreAffairesJour,
    BigDecimal chiffreAffairesMois,
    BigDecimal valeurStock
) {}
