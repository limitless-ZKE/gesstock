package com.gesstock.gesstock.ventes.dto;

import java.math.BigDecimal;

/**
 * @param prixUnitaire  prix réellement appliqué (négocié le cas échéant)
 * @param prixCatalogue prix catalogue au moment du document ; la remise négociée par unité vaut
 *                      {@code prixCatalogue - prixUnitaire}. Égal à {@code prixUnitaire} sur les anciennes lignes.
 */
public record LigneDocumentResponse(
    Long articleId,
    String reference,
    String designation,
    Long uniteVenteId,
    Integer quantite,
    BigDecimal prixUnitaire,
    BigDecimal montantLigne,
    Long promotionId,
    BigDecimal remiseUnitaire,
    BigDecimal prixCatalogue
) {}
