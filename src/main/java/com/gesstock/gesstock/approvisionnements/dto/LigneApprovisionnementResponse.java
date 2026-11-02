package com.gesstock.gesstock.approvisionnements.dto;

import java.math.BigDecimal;

/** @param prixVente prix de revente saisi sur le bon, null s'il n'a pas été saisi */
public record LigneApprovisionnementResponse(
    Long id,
    Long articleId,
    String referenceArticle,
    String designationArticle,
    BigDecimal quantite,
    BigDecimal prixUnitaire,
    BigDecimal montantLigne,
    BigDecimal prixVente
) {}
