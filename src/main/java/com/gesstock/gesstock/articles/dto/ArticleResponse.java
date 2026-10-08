package com.gesstock.gesstock.articles.dto;

import java.math.BigDecimal;

public record ArticleResponse(
    Long id,
    String reference,
    String designation,
    BigDecimal prixAchat,
    BigDecimal coutMoyen,
    BigDecimal prixVente,
    Integer quantiteStock,
    Integer seuilAlerteStock,
    Long categorieId,
    String categorie,
    Long uniteMesureId,
    String uniteMesure
) {}
