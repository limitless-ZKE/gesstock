package com.gesstock.gesstock.referentiels.dto;

import com.gesstock.gesstock.entities.StatutSessionCaisse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record SessionCaisseResponse(
    Long id,
    String code,
    LocalDate journee,
    LocalDateTime ouverteLe,
    LocalDateTime fermeeLe,
    BigDecimal fondDeCaisse,
    BigDecimal montantCloture,
    BigDecimal encaissementsEspeces,
    BigDecimal reglementsClientsEspeces,
    BigDecimal sortiesCaisse,
    BigDecimal soldeTheorique,
    BigDecimal ecartCloture,
    StatutSessionCaisse statut,
    Long ouverteParId,
    String ouvertePar,
    Long fermeeParId,
    String fermeePar,
    boolean journeeDepassee
) {}
