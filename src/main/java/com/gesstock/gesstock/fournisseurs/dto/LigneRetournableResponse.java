package com.gesstock.gesstock.fournisseurs.dto;

import java.math.BigDecimal;

/**
 * Une ligne d'un bon reçu, avec ce qu'on peut encore rendre. {@code stockActuel} est indicatif : c'est la validation qui
 * vérifie qu'il reste assez en rayon (le reste a pu être vendu).
 */
public record LigneRetournableResponse(
    Long ligneApprovisionnementId,
    Long articleId,
    String reference,
    String designation,
    int quantiteRecue,
    int quantiteDejaRetournee,
    int quantiteRetournable,
    BigDecimal coutUnitaire,
    int stockActuel
) {}
