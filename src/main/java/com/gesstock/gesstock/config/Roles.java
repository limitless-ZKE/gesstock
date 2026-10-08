package com.gesstock.gesstock.config;

/**
 * Les rôles de l'application, sans le préfixe « ROLE_ ». Une seule définition : pas de chaîne recopiée dans les règles.
 * <ul>
 *   <li>ADMIN : configuration, utilisateurs, tout le reste</li>
 *   <li>MANAGER : responsable commerce — annulations, remises, rapports, journal</li>
 *   <li>GESTIONNAIRE : stock, fournisseurs, approvisionnements, rapports</li>
 *   <li>CAISSIER : caisse, ventes, clients, règlements</li>
 * </ul>
 */
public final class Roles {

    public static final String ADMIN = "ADMIN";
    public static final String MANAGER = "MANAGER";
    public static final String GESTIONNAIRE = "GESTIONNAIRE";
    public static final String CAISSIER = "CAISSIER";

    private Roles() {}
}
