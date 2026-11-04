package com.gesstock.gesstock.statistiques.dto;

import com.gesstock.gesstock.entities.StatutSessionCaisse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record SessionCaisseRapportResponse(
    Long sessionId,
    String code,
    LocalDate journee,
    String caissier,
    StatutSessionCaisse statut,
    BigDecimal fondDeCaisse,
    BigDecimal chiffreAffaires,
    BigDecimal montantEncaisse,
    BigDecimal reglementsClientsEspeces,
    BigDecimal entreesDeFonds,
    BigDecimal sortiesCaisse,
    BigDecimal montantCloture,
    long nombreVentes,
    LocalDateTime ouverteLe,
    LocalDateTime fermeeLe
) {}
