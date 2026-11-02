package com.gesstock.gesstock.ventes.dto;

import java.math.BigDecimal;

/**
 * @param prixUnitaire  prix réellement appliqué (négocié le cas échéant)
 * @param montantLigne  montant net de la ligne : c'est lui qui compte (il vaut le total négocié quand la remise a été faite
 *                      sur la ligne entière)
 * @param prixCatalogue prix catalogue au moment du document ; la remise négociée par unité vaut
 *                      {@code prixCatalogue - prixUnitaire}. Égal à {@code prixUnitaire} sur les anciennes lignes.
 */
public record LigneDocumentResponse(
    Long articleId,
    String reference,
    String designation,
    Long uniteVenteId,
    BigDecimal quantite,
    BigDecimal prixUnitaire,
    BigDecimal montantLigne,
    Long promotionId,
    BigDecimal remiseUnitaire,
    BigDecimal prixCatalogue
) {}
