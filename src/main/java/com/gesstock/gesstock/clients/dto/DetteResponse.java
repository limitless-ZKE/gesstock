package com.gesstock.gesstock.clients.dto;

import com.gesstock.gesstock.entities.StatutDette;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DetteResponse(
    Long id,
    LocalDate dateCreation,
    Long venteId,
    String numeroVente,
    BigDecimal montantInitial,
    BigDecimal montantRegle,
    BigDecimal solde,
    StatutDette statut
) {}
