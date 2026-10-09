package com.gesstock.gesstock.caisse.dto;

import java.math.BigDecimal;

/**
 * Totaux de la période : somme des journées. {@code especesComptees} et {@code ecarts} ne portent que sur les sessions
 * clôturées ; {@code sessionsNonCloturees} dit combien manquent à ces deux totaux.
 */
public record TotauxJournalCaisse(
    int nombreSessions,
    int sessionsNonCloturees,
    long nombreVentes,
    long nombreVentesAnnulees,
    BigDecimal fondsDeCaisse,
    BigDecimal encaissementsEspeces,
    BigDecimal reglementsClientsEspeces,
    BigDecimal sorties,
    BigDecimal soldeTheorique,
    BigDecimal especesComptees,
    BigDecimal ecarts,
    BigDecimal autresEncaissements,
    BigDecimal ventesACredit
) {}
