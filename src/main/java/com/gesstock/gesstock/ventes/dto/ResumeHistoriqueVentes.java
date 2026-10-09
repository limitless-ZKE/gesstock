package com.gesstock.gesstock.ventes.dto;

import java.math.BigDecimal;

/**
 * @param nombreValidees  ventes validées de la période
 * @param chiffreAffaires total TTC des ventes validées (les ventes annulées n'y figurent pas)
 * @param remises         remises accordées sur les ventes validées (négociées avec le client + promotions)
 * @param nombreAnnulees  ventes annulées
 * @param montantAnnule   total TTC des ventes annulées, à titre d'information
 */
public record ResumeHistoriqueVentes(
    long nombreValidees,
    BigDecimal chiffreAffaires,
    BigDecimal remises,
    long nombreAnnulees,
    BigDecimal montantAnnule
) {}
