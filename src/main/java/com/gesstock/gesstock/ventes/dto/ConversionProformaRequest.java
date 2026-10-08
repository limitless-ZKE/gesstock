package com.gesstock.gesstock.ventes.dto;

import com.gesstock.gesstock.entities.ModeReglement;

import java.math.BigDecimal;

/**
 * Conversion d'un proforma accepté en vente. Au comptant, le règlement est OBLIGATOIRE (mode et montant reçu), comme
 * pour une vente directe ; à crédit, il n'y en a pas (le client règlera via sa dette).
 */
public record ConversionProformaRequest(boolean aCredit, ModeReglement modeReglement, BigDecimal montantRecu) {}
