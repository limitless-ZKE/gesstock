package com.gesstock.gesstock.caisse.dto;

import com.gesstock.gesstock.entities.ModeReglement;

import java.math.BigDecimal;

public record EncaissementHorsEspeces(ModeReglement mode, BigDecimal montant) {}
