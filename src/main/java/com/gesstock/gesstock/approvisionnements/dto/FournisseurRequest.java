package com.gesstock.gesstock.approvisionnements.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FournisseurRequest(
    @NotBlank @Size(max = 120) String nom,
    @Size(max = 30) String telephone,
    @Email @Size(max = 180) String email,
    @Size(max = 255) String adresse
) {}
