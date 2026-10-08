package com.gesstock.gesstock.fournisseurs.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AvoirFournisseurResponse(
    Long id,
    LocalDateTime dateCreation,
    BigDecimal montant,
    BigDecimal montantUtilise,
    BigDecimal restant,
    String motif,
    String creePar
) {}
