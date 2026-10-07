package com.gesstock.gesstock.referentiels.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UniteMesureRequest(
    @NotBlank @Size(max = 20) String code,
    @NotBlank @Size(max = 80) String libelle
) {}
