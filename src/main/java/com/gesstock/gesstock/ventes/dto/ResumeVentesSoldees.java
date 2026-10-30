package com.gesstock.gesstock.ventes.dto;

import java.math.BigDecimal;

/**
 * @param nombre               ventes de la période entièrement payées
 * @param montant              leur total TTC
 * @param nombreComptant       dont ventes au comptant
 * @param montantComptant      leur total
 * @param nombreCreditsSoldes  dont ventes à crédit dont la dette est réglée
 * @param montantCreditsSoldes leur total
 */
public record ResumeVentesSoldees(
    long nombre,
    BigDecimal montant,
    long nombreComptant,
    BigDecimal montantComptant,
    long nombreCreditsSoldes,
    BigDecimal montantCreditsSoldes
) {}
