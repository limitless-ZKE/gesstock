package com.gesstock.gesstock.ventes.dto;

import com.gesstock.gesstock.entities.ModeReglement;

import java.math.BigDecimal;

/** Ce qui a été encaissé par un moyen de paiement (espèces, Mobile Money…) : nombre de paiements et montant. */
public record TotalParMode(ModeReglement mode, long nombre, BigDecimal montant) {}
