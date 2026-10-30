package com.gesstock.gesstock.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;

import java.util.Set;

/**
 * Qui un compte peut consulter dans les états par vendeur (historiques des ventes et des règlements, journal de caisse) : un caissier
 * ne voit que lui-même, un responsable (administrateur, gérant, gestionnaire) qui il veut. Le serveur l'impose, quoi que l'écran envoie.
 */
public final class PerimetreVendeur {

    public static final Set<String> ROLES_RESPONSABLES = Set.of("ROLE_ADMIN", "ROLE_MANAGER", "ROLE_GESTIONNAIRE");

    private PerimetreVendeur() {
    }

    public static boolean responsable(Authentication authentication) {
        return authentication.getAuthorities().stream().anyMatch(droit -> ROLES_RESPONSABLES.contains(droit.getAuthority()));
    }

    /**
     * Le vendeur dont on sert les chiffres : celui qui est nommé pour un responsable ({@code null} : tous les vendeurs), le compte
     * connecté pour un caissier. Un caissier qui en nomme un autre est refusé (403).
     */
    public static String vendeurAutorise(Authentication authentication, String demande) {
        boolean nomme = demande != null && !demande.isBlank();
        if (responsable(authentication)) {
            return nomme ? demande.trim() : null;
        }
        if (nomme && !demande.trim().equalsIgnoreCase(authentication.getName())) {
            throw new AccessDeniedException("Un caissier ne peut consulter que ses propres ventes et règlements.");
        }
        return authentication.getName();
    }
}
