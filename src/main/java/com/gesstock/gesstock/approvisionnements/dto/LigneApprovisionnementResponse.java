package com.gesstock.gesstock.approvisionnements.dto;

import java.math.BigDecimal;

public record LigneApprovisionnementResponse(
    Long id,
    Long articleId,
    String referenceArticle,
    String designationArticle,
    Integer quantite,
    BigDecimal prixUnitaire,
    BigDecimal montantLigne
) {}
