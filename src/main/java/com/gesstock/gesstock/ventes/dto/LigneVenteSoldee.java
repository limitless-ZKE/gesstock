package com.gesstock.gesstock.ventes.dto;

import com.gesstock.gesstock.entities.ModeReglement;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Une vente entièrement payée : au comptant, ou à crédit dont la dette est réglée jusqu'au dernier franc.
 *
 * @param modeReglement moyen de paiement d'une vente au comptant ; nul pour une vente à crédit (payée par ses règlements)
 * @param soldeeLe      le moment où plus rien n'était dû : la vente elle-même au comptant, le dernier règlement pour un crédit
 */
public record LigneVenteSoldee(
    Long id,
    String code,
    LocalDateTime dateVente,
    String client,
    String vendeur,
    boolean aCredit,
    ModeReglement modeReglement,
    BigDecimal montantTotal,
    LocalDateTime soldeeLe
) {}
