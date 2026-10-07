package com.gesstock.gesstock.referentiels.mapper;

import com.gesstock.gesstock.entities.Role;
import com.gesstock.gesstock.entities.Utilisateur;
import com.gesstock.gesstock.referentiels.dto.UtilisateurResponse;

import java.util.Comparator;
import java.util.stream.Collectors;

public final class UtilisateurMapper {

    private UtilisateurMapper() {}

    public static UtilisateurResponse toResponse(Utilisateur utilisateur) {
        var roles = utilisateur.getRoles().stream()
            .sorted(Comparator.comparing(Role::getName))
            .map(RoleMapper::toResponse)
            .collect(Collectors.toCollection(java.util.LinkedHashSet::new));

        return new UtilisateurResponse(
            utilisateur.getId(),
            utilisateur.getUsername(),
            utilisateur.isActif(),
            roles
        );
    }
}
