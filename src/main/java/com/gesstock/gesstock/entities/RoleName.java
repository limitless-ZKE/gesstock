package com.gesstock.gesstock.entities;

public enum RoleName {
    ROLE_ADMIN,       // Accès total : Rapports, Prix, Modif Stock, Création Users
    ROLE_MANAGER,     // Gestion Stock, Approvisionnements, Fournisseurs
    ROLE_CAISSIER     // Ventes uniquement, consultation catalogue basique
}