package com.gesstock.gesstock.statistiques.dto;

import java.math.BigDecimal;

public record MargeCategorieResponse(
    Long categorieId,
    String categorie,
    BigDecimal chiffreAffaires,
    BigDecimal coutAchat,
    BigDecimal margeBrute
) {}
