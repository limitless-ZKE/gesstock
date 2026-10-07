package com.gesstock.gesstock.approvisionnements.dto;

import com.gesstock.gesstock.entities.StatutApprovisionnement;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ApprovisionnementResponse(
    Long id,
    String numero,
    LocalDate dateApprovisionnement,
    StatutApprovisionnement statut,
    Long fournisseurId,
    String fournisseur,
    BigDecimal montantTotal,
    List<LigneApprovisionnementResponse> lignes
) {}
