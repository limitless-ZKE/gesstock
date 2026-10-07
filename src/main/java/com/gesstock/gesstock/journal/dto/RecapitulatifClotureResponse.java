package com.gesstock.gesstock.journal.dto;

import com.gesstock.gesstock.entities.StatutSessionCaisse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record RecapitulatifClotureResponse(
    Long sessionId,
    LocalDate journee,
    StatutSessionCaisse statut,
    BigDecimal fondDeCaisse,
    BigDecimal montantCloture,
    long nombreVentesValidees,
    BigDecimal chiffreAffaires,
    long nombreOperationsJournalisees,
    LocalDateTime ouverteLe,
    LocalDateTime fermeeLe,
    List<JournalActiviteResponse> activites
) {}
