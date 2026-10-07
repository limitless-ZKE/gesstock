package com.gesstock.gesstock.referentiels.dto;

import java.util.Set;

public record UtilisateurResponse(
    Long id,
    String username,
    boolean actif,
    Set<RoleResponse> roles
) {}
