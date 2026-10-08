package com.gesstock.gesstock.fournisseurs.dto;

import java.math.BigDecimal;

public record LigneRetourFournisseurResponse(
    Long ligneApprovisionnementId,
    Long articleId,
    String reference,
    String designation,
    int quantite,
    BigDecimal coutUnitaire,
    BigDecimal montant
) {}
