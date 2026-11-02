package com.gesstock.gesstock.statistiques.dto;

import java.math.BigDecimal;

public record MargeArticleResponse(
    Long articleId,
    String reference,
    String designation,
    BigDecimal quantiteVendue,
    BigDecimal chiffreAffaires,
    BigDecimal coutAchat,
    BigDecimal margeBrute
) {}
