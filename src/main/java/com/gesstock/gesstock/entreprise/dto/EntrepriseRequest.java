package com.gesstock.gesstock.entreprise.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Textes vides ou ne contenant que des espaces : enregistrés comme absents. */
public record EntrepriseRequest(
    @NotBlank @Size(max = 150) String nom,
    @Size(max = 300) String adresse,
    @Size(max = 40) String telephone,
    @Email @Size(max = 180) String email,
    @Size(max = 80) String identifiantLegal,
    @Size(max = 180) String siteWeb,
    @Size(max = 200) String piedDePage,
    boolean logoSurTickets
) {}
