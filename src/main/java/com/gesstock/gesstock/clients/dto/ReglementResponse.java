package com.gesstock.gesstock.clients.dto;

import com.gesstock.gesstock.entities.ModeReglement;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ReglementResponse(
    Long id,
    Long detteId,
    Long clientId,
    BigDecimal montant,
    ModeReglement modeReglement,
    String reference,
    LocalDateTime dateReglement,
    Long recuParId,
    String recuPar
) {}
