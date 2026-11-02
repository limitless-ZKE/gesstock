package com.gesstock.gesstock.ventes.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record ProformaRequest(
    @NotBlank @Size(max = 50) String numero,
    LocalDate dateEmission,
    LocalDate dateExpiration,
    Long clientId,
    @NotEmpty List<@Valid LigneDocumentRequest> lignes
) {}
