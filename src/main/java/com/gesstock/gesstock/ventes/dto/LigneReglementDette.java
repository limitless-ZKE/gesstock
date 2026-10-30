package com.gesstock.gesstock.ventes.dto;

import com.gesstock.gesstock.entities.ModeReglement;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Un paiement reçu sur la dette d'un client.
 *
 * @param vente        numéro de la vente à crédit d'origine (nul pour une dette sans vente)
 * @param recuPar      le compte qui a encaissé le paiement
 * @param resteApres   ce qui restait dû sur la dette juste après ce paiement ; nul si la dette a été annulée depuis
 * @param detteAnnulee la vente à crédit a été annulée après ce paiement
 */
public record LigneReglementDette(
    Long id,
    LocalDateTime dateReglement,
    String client,
    String vente,
    BigDecimal montant,
    ModeReglement modeReglement,
    String reference,
    String recuPar,
    BigDecimal resteApres,
    boolean detteAnnulee
) {}
