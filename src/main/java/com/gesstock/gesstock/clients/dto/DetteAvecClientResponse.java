package com.gesstock.gesstock.clients.dto;

import com.gesstock.gesstock.entities.StatutDette;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Une dette accompagnée de son client, pour afficher toutes les créances en un seul appel. */
public record DetteAvecClientResponse(
    Long id,
    Long clientId,
    String client,
    LocalDate dateCreation,
    Long venteId,
    String numeroVente,
    BigDecimal montantInitial,
    BigDecimal montantRegle,
    BigDecimal solde,
    StatutDette statut
) {}
