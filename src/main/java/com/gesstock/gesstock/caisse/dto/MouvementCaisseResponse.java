package com.gesstock.gesstock.caisse.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MouvementCaisseResponse(
    Long id,
    Long sessionId,
    String type,
    String motif,
    String beneficiaire,
    BigDecimal montant,
    LocalDateTime dateMouvement,
    String effectuePar
) {}
