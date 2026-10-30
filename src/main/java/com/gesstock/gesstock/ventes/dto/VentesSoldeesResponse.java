package com.gesstock.gesstock.ventes.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Les ventes de la période (date de la vente) qui sont aujourd'hui entièrement payées : une vente à crédit y entre le jour où sa
 * dette est réglée, à la date où elle a été faite. Les ventes annulées et les crédits encore dus n'y sont pas.
 */
public record VentesSoldeesResponse(
    LocalDate debut,
    LocalDate fin,
    ResumeVentesSoldees resume,
    List<LigneVenteSoldee> ventes
) {}
