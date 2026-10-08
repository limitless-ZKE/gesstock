package com.gesstock.gesstock.inventaires.dto;

import com.gesstock.gesstock.entities.MotifAjustementStock;

import java.math.BigDecimal;

/** Une ligne d'inventaire. {@code stockCompte}, {@code ecart} et {@code valeurEcart} sont null tant que l'article n'est pas compté. */
public record LigneInventaireResponse(
    Long articleId,
    String reference,
    String designation,
    String categorie,
    String unite,
    int stockTheorique,
    Integer stockCompte,
    Integer ecart,
    BigDecimal coutUnitaire,
    BigDecimal valeurEcart,
    MotifAjustementStock motif,
    String commentaire,
    Integer ecartApplique
) {}
