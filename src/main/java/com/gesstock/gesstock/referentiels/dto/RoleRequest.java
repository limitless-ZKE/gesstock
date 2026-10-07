package com.gesstock.gesstock.referentiels.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RoleRequest(
    @NotBlank
    @Size(max = 30)
    @Pattern(regexp = "[A-Za-z][A-Za-z0-9_-]*", message = "Le nom du rôle contient des caractères invalides")
    String name
) {}
