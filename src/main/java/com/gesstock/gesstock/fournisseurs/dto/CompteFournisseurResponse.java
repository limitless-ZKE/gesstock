package com.gesstock.gesstock.fournisseurs.dto;

import java.math.BigDecimal;
import java.util.List;

public record CompteFournisseurResponse(
    Long fournisseurId,
    String nom,
    BigDecimal soldeDu,
    BigDecimal creditDisponible,
    List<DetteFournisseurResponse> dettes,
    List<AvoirFournisseurResponse> avoirs
) {}
