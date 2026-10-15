package com.gesstock.gesstock.caisse.dto;

import java.math.BigDecimal;

/** Totaux d'un caissier sur la période ; mêmes définitions que {@link JournalCaissierJour}. */
public record TotauxJournalCaissier(
    int nombreJours,
    long nombreVentes,
    long nombreVentesAnnulees,
    BigDecimal ventesEspeces,
    BigDecimal reglementsEspeces,
    BigDecimal sorties,
    BigDecimal especesARemettre,
    BigDecimal autresEncaissements,
    BigDecimal ventesACredit
) {}
