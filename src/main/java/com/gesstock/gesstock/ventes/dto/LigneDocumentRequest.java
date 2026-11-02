package com.gesstock.gesstock.ventes.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * @param quantite       quantité vendue, 3 décimales au plus
 * @param prixUnitaire   prix négocié par unité (facultatif, deux décimales au plus). Il ne peut pas dépasser le prix
 *                       catalogue : l'écart est la remise négociée, affichée sur les documents.
 * @param montantNegocie prix négocié pour la ligne ENTIÈRE (facultatif, deux décimales au plus) : remise faite sur le total
 *                       de l'article. Il remplace {@code prixUnitaire} et la promotion ; il ne peut pas dépasser le total
 *                       au prix catalogue.
 */
public record LigneDocumentRequest(
    @NotNull Long articleId,
    Long uniteVenteId,
    @NotNull @Positive @Digits(integer = 12, fraction = 3) BigDecimal quantite,
    @DecimalMin("0.0") @Digits(integer = 15, fraction = 2) BigDecimal prixUnitaire,
    Long promotionId,
    @DecimalMin("0.0") @Digits(integer = 15, fraction = 2) BigDecimal montantNegocie
) {
    public LigneDocumentRequest(Long articleId, Long uniteVenteId, BigDecimal quantite, BigDecimal prixUnitaire, Long promotionId) {
        this(articleId, uniteVenteId, quantite, prixUnitaire, promotionId, null);
    }
}
