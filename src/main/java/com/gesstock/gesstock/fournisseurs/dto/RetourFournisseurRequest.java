package com.gesstock.gesstock.fournisseurs.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record RetourFournisseurRequest(
    @NotNull Long approvisionnementId,
    @NotBlank @Size(max = 300) String motif,
    @NotEmpty List<@Valid @NotNull LigneRetourFournisseurRequest> lignes
) {}
