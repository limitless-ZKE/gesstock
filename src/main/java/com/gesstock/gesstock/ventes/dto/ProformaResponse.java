package com.gesstock.gesstock.ventes.dto;

import com.gesstock.gesstock.entities.StatutProforma;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ProformaResponse(
    Long id,
    String numero,
    LocalDate dateEmission,
    LocalDate dateExpiration,
    StatutProforma statut,
    Long clientId,
    String client,
    BigDecimal montantTotal,
    List<LigneDocumentResponse> lignes,
    Long venteId
) {}
