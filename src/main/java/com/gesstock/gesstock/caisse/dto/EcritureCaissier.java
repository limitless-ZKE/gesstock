package com.gesstock.gesstock.caisse.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Une ligne du journal d'un caissier : exactement une des deux colonnes, entrée ou sortie, est renseignée. Le cumul additionne
 * ses propres lignes depuis zéro (ce n'est pas le solde du tiroir, qui dépend aussi des autres).
 */
public record EcritureCaissier(
    LocalDateTime date,
    TypeEcritureCaisse type,
    String libelle,
    String reference,
    BigDecimal entree,
    BigDecimal sortie,
    BigDecimal cumul
) {}
