package com.gesstock.gesstock.inventaires.dto;

import com.gesstock.gesstock.entities.PerimetreInventaire;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * @param perimetre   TOUT le catalogue, ou une CATEGORIE (ses sous-catégories comprises)
 * @param categorieId obligatoire si le périmètre est CATEGORIE
 */
public record InventaireRequest(
    @NotNull PerimetreInventaire perimetre,
    Long categorieId,
    @Size(max = 500) String commentaire
) {}
