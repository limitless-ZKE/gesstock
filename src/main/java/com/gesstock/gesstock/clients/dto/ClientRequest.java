package com.gesstock.gesstock.clients.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ClientRequest(
    @NotBlank @Size(max = 120) String nom,
    @Email @Size(max = 180) String email,
    @Size(max = 30) String telephone,
    @Size(max = 255) String adresse
) {}
