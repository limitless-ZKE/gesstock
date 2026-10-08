package com.gesstock.gesstock.entreprise.dto;

import java.time.LocalDateTime;

/** {@code logo} est null quand l'entreprise n'a pas de logo. */
public record EntrepriseResponse(
    String nom,
    String adresse,
    String telephone,
    String email,
    String identifiantLegal,
    String siteWeb,
    String piedDePage,
    boolean logoSurTickets,
    LogoResponse logo,
    LocalDateTime misAJourLe,
    String misAJourPar
) {}
