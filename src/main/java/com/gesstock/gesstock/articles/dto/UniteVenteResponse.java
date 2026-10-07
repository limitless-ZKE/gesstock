package com.gesstock.gesstock.articles.dto;

import java.math.BigDecimal;

public record UniteVenteResponse(
    Long id,
    Long articleId,
    String code,
    Long uniteMesureId,
    String uniteMesureCode,
    String uniteMesureLibelle,
    BigDecimal facteurConversion,
    BigDecimal prixVente,
    boolean active
) {}
