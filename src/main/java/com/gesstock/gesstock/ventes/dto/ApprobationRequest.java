package com.gesstock.gesstock.ventes.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Accord d'un responsable donné au comptoir : son identifiant et son mot de passe, vérifiés par le serveur sans ouvrir de
 * session. Le mot de passe n'apparaît jamais dans les journaux (voir {@link #toString()}).
 */
public record ApprobationRequest(
    @NotBlank @Size(max = 80) String username,
    @NotBlank @Size(max = 200) String motDePasse
) {
    @Override
    public String toString() {
        return "ApprobationRequest[username=" + username + ", motDePasse=***]";
    }
}
