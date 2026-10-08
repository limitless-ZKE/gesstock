package com.gesstock.gesstock.common.exception;

/**
 * L'opération dépasse ce que le vendeur peut décider seul (remise au-delà du plafond de son rôle, prix sous le coût) :
 * l'accord d'un responsable est nécessaire. Répondue en 409 avec le code {@code APPROBATION_REQUISE} ; le front ouvre
 * alors la saisie de l'identifiant et du mot de passe du responsable, puis renvoie la même demande avec cet accord.
 */
public class ApprobationRequiseException extends ConflitRessourceException {
    public ApprobationRequiseException(String message) {
        super(message);
    }
}
