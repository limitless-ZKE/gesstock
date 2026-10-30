package com.gesstock.gesstock.ventes.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * @param nombre        paiements reçus sur des dettes pendant la période
 * @param total         leur montant
 * @param dettesSoldees dettes que l'un de ces paiements a réglées jusqu'au dernier franc
 * @param parMode       le détail par moyen de paiement (seuls les moyens utilisés, dans l'ordre habituel)
 */
public record ResumeReglementsDettes(
    long nombre,
    BigDecimal total,
    long dettesSoldees,
    List<TotalParMode> parMode
) {}
