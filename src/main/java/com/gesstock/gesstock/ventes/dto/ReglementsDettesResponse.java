package com.gesstock.gesstock.ventes.dto;

import java.time.LocalDate;
import java.util.List;

/** Les paiements reçus sur les dettes des clients pendant la période (date du paiement), les plus récents d'abord. */
public record ReglementsDettesResponse(
    LocalDate debut,
    LocalDate fin,
    ResumeReglementsDettes resume,
    List<LigneReglementDette> reglements
) {}
