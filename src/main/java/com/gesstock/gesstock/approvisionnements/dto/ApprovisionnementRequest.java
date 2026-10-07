package com.gesstock.gesstock.approvisionnements.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record ApprovisionnementRequest(
    @NotBlank @Size(max = 60) String numero,
    LocalDate dateApprovisionnement,
    @NotNull Long fournisseurId,
    @NotEmpty List<@Valid LigneApprovisionnementRequest> lignes
) {}
