package com.gesstock.gesstock.caisse.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Une ligne du journal. {@code entree} ou {@code sortie} est renseignée (jamais les deux) ; {@code solde} est le solde
 * théorique des espèces juste après cette écriture.
 */
public record EcritureCaisse(
    LocalDateTime date,
    TypeEcritureCaisse type,
    String libelle,
    String reference,
    String utilisateur,
    BigDecimal entree,
    BigDecimal sortie,
    BigDecimal solde
) {}
