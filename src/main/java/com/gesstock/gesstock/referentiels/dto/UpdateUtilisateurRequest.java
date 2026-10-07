package com.gesstock.gesstock.referentiels.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record UpdateUtilisateurRequest(
    @NotBlank @Size(max = 80) String username,
    @Size(min = 8, max = 72) String password,
    Boolean actif,
    @NotEmpty Set<Long> roleIds
) {}
