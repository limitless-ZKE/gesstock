package com.gesstock.gesstock.fournisseurs.dto;

import com.gesstock.gesstock.entities.ModeReglement;
import com.gesstock.gesstock.entities.SourceReglementFournisseur;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** {@code modeReglement} est null pour un avoir imputé ; {@code avoirId} n'est renseigné que dans ce cas. */
public record ReglementFournisseurResponse(
    Long id,
    Long detteId,
    BigDecimal montant,
    SourceReglementFournisseur source,
    ModeReglement modeReglement,
    String reference,
    LocalDateTime dateReglement,
    String effectuePar,
    Long avoirId
) {}
