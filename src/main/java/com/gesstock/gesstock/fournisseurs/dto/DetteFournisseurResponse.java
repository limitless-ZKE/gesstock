package com.gesstock.gesstock.fournisseurs.dto;

import com.gesstock.gesstock.entities.StatutDette;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record DetteFournisseurResponse(
    Long id,
    LocalDate dateCreation,
    Long approvisionnementId,
    String numeroBon,
    BigDecimal montantInitial,
    BigDecimal montantRegle,
    BigDecimal solde,
    StatutDette statut,
    List<ReglementFournisseurResponse> reglements
) {}
